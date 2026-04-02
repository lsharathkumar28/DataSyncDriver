package com.outreach.datasyncdriver.controller;

import com.outreach.datasyncdriver.service.InitialSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Sync", description = "Synchronization management endpoints")
public class SyncController {

    private final InitialSyncService initialSyncService;

    @PostMapping("/initial")
    @Operation(summary = "Trigger initial sync",
               description = "Fetches all users from DataSynchronizer via REST and loads them into all registered connectors")
    @ApiResponse(responseCode = "200", description = "Initial sync completed")
    @ApiResponse(responseCode = "500", description = "Initial sync failed — connector write error")
    public ResponseEntity<Map<String, Object>> triggerInitialSync() {
        try {
            int count = initialSyncService.runInitialSync();
            return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "usersSynchronized", count
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "failed",
                    "error", e.getMessage()
            ));
        }
    }
}

