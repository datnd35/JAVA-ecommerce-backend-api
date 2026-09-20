package com.myshop.common;

public class ResultUtil {

    public static <T> ResultMessage<T> success(T data) {
        return new ResultMessage<>(200, "Success", data);
    }

    public static <T> ResultMessage<T> error(int code, String message) {
        return new ResultMessage<>(code, message, null);
    }

    public static class ResultMessage<T> {
        private int code;
        private String message;
        private T data;

        public ResultMessage(int code, String message, T data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }
}
