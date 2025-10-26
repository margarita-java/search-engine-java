package searchengine.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import searchengine.dto.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ApiError handleBadRequest(BadRequestException ex) {
        log.warn("BadRequest: {}", ex.getMessage());
        return new ApiError(false, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ApiError handleNotFound(NotFoundException ex) {
        log.warn("NotFound: {}", ex.getMessage());
        return new ApiError(false, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiError handleOther(Exception ex) {
        // Общее поведение для неожиданных исключений.
        log.error("Unhandled exception: ", ex);
        return new ApiError(false, "Внутренняя ошибка сервера");
    }
}
