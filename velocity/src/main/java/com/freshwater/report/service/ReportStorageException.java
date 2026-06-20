package com.freshwater.report.service;

/**
 * 数据访问异常的非受检包装，便于 Java API 调用方处理。
 */
public class ReportStorageException extends RuntimeException {

    public ReportStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
