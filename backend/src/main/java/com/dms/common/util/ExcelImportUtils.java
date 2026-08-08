package com.dms.common.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelImportUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<Map<String, Object>> importFromExcel(InputStream inputStream, String fileName) throws Exception {
        try (Workbook workbook = createWorkbook(inputStream, fileName)) {
            Sheet sheet = workbook.getSheetAt(0);
            return parseSheet(sheet);
        }
    }

    private static Workbook createWorkbook(InputStream inputStream, String fileName) throws Exception {
        if (fileName.toLowerCase().endsWith(".xlsx")) {
            return new XSSFWorkbook(inputStream);
        } else if (fileName.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(inputStream);
        } else {
            throw new IllegalArgumentException("不支持的文件格式，仅支持 .xlsx 和 .xls");
        }
    }

    private static List<Map<String, Object>> parseSheet(Sheet sheet) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
            return result;
        }

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return result;
        }

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            headers.add(getCellStringValue(cell).trim());
        }

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isEmptyRow(row)) {
                continue;
            }

            Map<String, Object> rowData = new HashMap<>();
            for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                Cell cell = row.getCell(colIndex);
                String header = headers.get(colIndex);
                rowData.put(header, getCellValue(cell));
            }
            result.add(rowData);
        }

        return result;
    }

    private static boolean isEmptyRow(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 将 Excel 单元格值归一化为 yyyy-MM-dd 日期字符串，供原生 SQL 的 CAST(? AS date) 使用。
     * 日期格式单元格会被解析为 LocalDateTime，直接 toString 会得到 2026-01-31T00:00 而无法入库。
     */
    public static String toDateString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate().toString();
        }
        if (value instanceof LocalDate) {
            return value.toString();
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return null;
        }
        int sep = str.indexOf('T');
        if (sep > 0) {
            str = str.substring(0, sep);
        } else if (str.indexOf(' ') > 0) {
            str = str.substring(0, str.indexOf(' '));
        }
        return str;
    }

    private static Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                String str = cell.getStringCellValue().trim();
                return str.isEmpty() ? null : str;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue();
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
                    return (long) numValue;
                }
                return BigDecimal.valueOf(numValue);
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return cell.getNumericCellValue();
                }
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    private static String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        Object value = getCellValue(cell);
        return value == null ? "" : value.toString();
    }

    public static <T> List<T> convertToEntities(List<Map<String, Object>> data, Class<T> clazz, String[] fieldNames, String[] excelHeaders) throws Exception {
        List<T> entities = new ArrayList<>();
        Map<String, String> headerToField = new HashMap<>();
        for (int i = 0; i < excelHeaders.length && i < fieldNames.length; i++) {
            headerToField.put(excelHeaders[i], fieldNames[i]);
        }

        for (Map<String, Object> row : data) {
            T entity = clazz.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String fieldName = headerToField.get(entry.getKey());
                if (fieldName != null) {
                    setFieldValue(entity, fieldName, entry.getValue());
                }
            }
            entities.add(entity);
        }
        return entities;
    }

    private static void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        Field field = findField(obj.getClass(), fieldName);
        if (field == null) {
            return;
        }
        field.setAccessible(true);

        Class<?> type = field.getType();
        Object convertedValue = convertValue(value, type);
        field.set(obj, convertedValue);
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return findField(superClass, fieldName);
            }
            return null;
        }
    }

    /**
     * 将 Excel 单元格值转为目标字段类型，供各 Controller 的导入反射赋值复用。
     * Excel 中同一列可能是文本也可能是数字，直接 (Number) 强转会 ClassCastException。
     */
    public static Object coerce(Object value, Class<?> type) {
        return convertValue(value, type);
    }

    private static Object convertValue(Object value, Class<?> type) {
        if (value == null) {
            return null;
        }

        if (type == String.class) {
            return value.toString();
        } else if (type == Integer.class || type == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String str = value.toString().trim();
            return str.isEmpty() ? null : Integer.parseInt(str);
        } else if (type == Long.class || type == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            String str = value.toString().trim();
            return str.isEmpty() ? null : Long.parseLong(str);
        } else if (type == Double.class || type == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            String str = value.toString().trim();
            return str.isEmpty() ? null : Double.parseDouble(str);
        } else if (type == BigDecimal.class) {
            if (value instanceof BigDecimal) {
                return value;
            } else if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            String str = value.toString().trim();
            return str.isEmpty() ? null : new BigDecimal(str);
        } else if (type == Boolean.class || type == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            String str = value.toString().trim().toLowerCase();
            return "true".equals(str) || "是".equals(str) || "1".equals(str);
        } else if (type == LocalDate.class) {
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).toLocalDate();
            } else if (value instanceof LocalDate) {
                return value;
            }
            String str = value.toString().trim();
            return str.isEmpty() ? null : LocalDate.parse(str, DATE_FORMATTER);
        } else if (type == LocalDateTime.class) {
            if (value instanceof LocalDateTime) {
                return value;
            } else if (value instanceof LocalDate) {
                return ((LocalDate) value).atStartOfDay();
            }
            String str = value.toString().trim();
            return str.isEmpty() ? null : LocalDateTime.parse(str, DATETIME_FORMATTER);
        }

        return value;
    }
}