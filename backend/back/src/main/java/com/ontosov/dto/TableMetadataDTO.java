package com.ontosov.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class TableMetadataDTO {
    private String name;
    private List<ColumnMetadataDTO> columns = new ArrayList<>();

    public TableMetadataDTO(String name) {
        this.name = name;
    }

    public void addColumn(ColumnMetadataDTO column) {
        columns.add(column);
    }
}
