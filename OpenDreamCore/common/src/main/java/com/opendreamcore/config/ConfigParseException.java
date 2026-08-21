package com.opendreamcore.config;

/**
 * 配置解析异常：带行列信息，便于定位用户写错的配置。
 */
public class ConfigParseException extends RuntimeException {

    private final int line;
    private final int column;

    public ConfigParseException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public ConfigParseException(String message, Throwable cause) {
        super(message, cause);
        this.line = -1;
        this.column = -1;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    @Override
    public String getMessage() {
        if (line > 0) {
            return super.getMessage() + "（第 " + line + " 行" + (column > 0 ? " 第 " + column + " 列" : "") + "）";
        }
        return super.getMessage();
    }
}
