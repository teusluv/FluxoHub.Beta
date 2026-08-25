package br.com.pod.admin;

import br.com.pod.admin.dto.DashboardKpiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasAuthority('ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpis")
    @Operation(summary = "Métricas principais (KPIs) para o Dashboard Admin")
    public ResponseEntity<DashboardKpiResponse> getKpis() {
        return ResponseEntity.ok(dashboardService.getKpisDoDia());
    }
}
