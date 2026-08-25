# ADR-002: Estratégia de OCR

**Status:** Aceito  
**Data:** 2024-01

## Contexto

Canhotos de entrega em campo são fotografados em condições adversas: sol forte,
papel amassado/rasgado, iluminação irregular, câmeras de celular de baixa qualidade.
A extração do número da nota fiscal via OCR é crítica para o match automático.

## Decisão

**Primário:** Google Cloud Vision API (REST)  
**Fallback offline:** Tesseract.js (apenas para validação básica no device, não para match)

### Por que Google Cloud Vision e não Tesseract no servidor?

| Critério | Google Cloud Vision | Tesseract (servidor) |
|---|---|---|
| Acurácia em fotos de campo | Alta (>90% em testes) | Baixa (<60% sem pré-processamento) |
| Resistência a rotação | Automática | Requer pré-processamento |
| Custo | ~$1.50/1000 imagens | Infraestrutura própria |
| Latência | ~800ms | ~2-5s sem GPU |
| Manutenção | Zero | Alta (modelos, pré-processamento) |

Para o volume esperado (~500 entregas/dia = 15.000/mês), o custo do Cloud Vision é
~$22/mês — insignificante frente ao tempo humano economizado.

## Fluxo de OCR

1. Upload da imagem → salva no MinIO → retorna 200 imediatamente ao mobile
2. Job assíncrono (`@Async`) envia imagem para Cloud Vision
3. Extrai texto, busca padrões de número de NF com regex
4. Se confiança ≥ 70%: tenta match automático com `numero_nota_fiscal` da entrega
5. Se confiança < 70%: marca `necessita_revisao = TRUE` para revisão manual no admin

## Consequências

**Positivas:**
- Upload não bloqueia esperando OCR (async)
- Alta acurácia mesmo em condições adversas

**Negativas / Riscos:**
- Dependência de serviço externo (GCP). Mitigação: timeout de 10s, fallback para
  `necessita_revisao = TRUE` se o GCP não responder
- Custo variável com volume. Monitorar via Cloud Console
- `GOOGLE_APPLICATION_CREDENTIALS` deve ser secret no CI/CD — nunca commitar
