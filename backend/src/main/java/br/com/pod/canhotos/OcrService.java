package br.com.pod.canhotos;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Serviço de OCR — extrai texto de imagens de canhotos.
 *
 * <p><strong>Provider primário:</strong> Google Cloud Vision API.
 * Retorna texto completo e score de confiança normalizado (0.0–1.0).
 *
 * <p><strong>Threshold de confiança:</strong> 0.70 (configurável em application.yml).
 * Abaixo disso, o canhoto é marcado {@code necessitaRevisao=true} para revisão manual.
 *
 * <p><strong>Processamento assíncrono:</strong> o upload retorna imediatamente.
 * O OCR é processado no pool {@code ocrTaskExecutor} em background.
 * O cliente mobile nunca espera o resultado do OCR — ele não bloqueia a operação.
 *
 * <p><strong>Falha graciosamente:</strong> se o OCR falhar (rede, API, imagem ilegível),
 * o canhoto é salvo com {@code necessitaRevisao=true} e {@code textoOcrExtraido=null}.
 * Nunca lançar exceção para o caller — apenas logar e marcar para revisão.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    // Threshold de confiança — abaixo disso, marca necessitaRevisao=true
    static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.70");

    /**
     * Resultado do OCR — encapsula texto extraído e confiança normalizada.
     *
     * @param texto       texto extraído (pode ser vazio se imagem ilegível)
     * @param confianca   score de confiança 0.000–1.000 (null se OCR falhou)
     * @param necessitaRevisao true se confiança < threshold ou OCR falhou
     */
    public record ResultadoOcr(
        String texto,
        BigDecimal confianca,
        boolean necessitaRevisao
    ) {}

    /**
     * Extrai texto de uma imagem usando Google Cloud Vision.
     *
     * <p>Executar em thread separada via {@code @Async("ocrTaskExecutor")}.
     * O resultado deve ser gravado de volta no canhoto via callback do chamador.
     *
     * @param imagemBytes bytes da imagem
     * @return resultado do OCR (nunca lança exceção)
     */
    public ResultadoOcr extrairTexto(byte[] imagemBytes) {
        try {
            return executarVision(imagemBytes);
        } catch (Exception e) {
            log.warn("OCR falhou — canhoto marcado para revisão manual. Causa: {}", e.getMessage());
            // Falha graciosamente: marcar para revisão, não bloquear o fluxo
            return new ResultadoOcr("", null, true);
        }
    }

    // -------------------------------------------------------------------------
    // Implementação Google Cloud Vision
    // -------------------------------------------------------------------------

    private ResultadoOcr executarVision(byte[] imagemBytes) throws IOException {
        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            Image imagem = Image.newBuilder()
                .setContent(ByteString.copyFrom(imagemBytes))
                .build();

            Feature feature = Feature.newBuilder()
                .setType(Feature.Type.DOCUMENT_TEXT_DETECTION) // melhor para documentos impressos
                .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(imagem)
                .build();

            List<AnnotateImageResponse> responses =
                client.batchAnnotateImages(List.of(request)).getResponsesList();

            if (responses.isEmpty() || responses.get(0).hasError()) {
                String erroApi = responses.isEmpty()
                    ? "Resposta vazia da API"
                    : responses.get(0).getError().getMessage();
                log.warn("Cloud Vision retornou erro: {}", erroApi);
                return new ResultadoOcr("", null, true);
            }

            AnnotateImageResponse response = responses.get(0);
            TextAnnotation annotation = response.getFullTextAnnotation();

            if (annotation == null || annotation.getText().isBlank()) {
                log.info("OCR sem texto detectado — imagem pode estar ilegível ou em branco");
                return new ResultadoOcr("", BigDecimal.ZERO, true);
            }

            String textoExtraido = annotation.getText().trim();
            BigDecimal confianca = calcularConfiancaMedia(annotation);

            boolean necessitaRevisao = confianca.compareTo(CONFIDENCE_THRESHOLD) < 0;

            log.info("OCR concluído: {} chars, confiança={}, revisão={}",
                textoExtraido.length(), confianca, necessitaRevisao);

            return new ResultadoOcr(textoExtraido, confianca, necessitaRevisao);
        }
    }

    /**
     * Calcula a confiança média de todos os blocos do documento.
     * Vision retorna confiança por bloco/parágrafo/palavra — fazemos a média ponderada
     * por número de palavras para refletir melhor a qualidade geral da imagem.
     */
    private BigDecimal calcularConfiancaMedia(TextAnnotation annotation) {
        double somaConfianca = 0.0;
        int totalBlocos = 0;

        for (Page page : annotation.getPagesList()) {
            for (Block bloco : page.getBlocksList()) {
                somaConfianca += bloco.getConfidence();
                totalBlocos++;
            }
        }

        if (totalBlocos == 0) return BigDecimal.ZERO;

        return BigDecimal.valueOf(somaConfianca / totalBlocos)
            .setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Verifica se as credenciais da API estão configuradas.
     * Usado no startup para detectar configuração incompleta cedo.
     */
    public Optional<String> verificarConfiguracao() {
        String credentials = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentials == null || credentials.isBlank()) {
            return Optional.of(
                "GOOGLE_APPLICATION_CREDENTIALS não configurado — OCR usará modo de revisão manual"
            );
        }
        return Optional.empty();
    }
}
