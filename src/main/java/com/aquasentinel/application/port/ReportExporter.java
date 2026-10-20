package com.aquasentinel.application.port;

import com.aquasentinel.application.dto.ReportSnapshot;

import java.io.OutputStream;

public interface ReportExporter {
    void export(ReportSnapshot snapshot, OutputStream outputStream);
    String getContentType();
    String getFileExtension();
}
