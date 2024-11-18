package com.ontosov.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ColumnMetadataDTO {
    private String name;
    private String type;
}
