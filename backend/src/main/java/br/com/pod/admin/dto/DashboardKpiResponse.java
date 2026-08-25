package br.com.pod.admin.dto;

import java.util.List;

public record DashboardKpiResponse(
        int totalEntregasHoje,
        int entregasComCanhotoHoje,
        double percentualConclusao,
        double tempoMedioSincronizacaoMinutos,
        List<RankingMotorista> rankingMotoristas
) {
    public record RankingMotorista(String motoristaId, String motoristaNome, long quantidadeCanhotos) {}
}
