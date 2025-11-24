package searchengine.exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import javax.servlet.http.HttpServletRequest;
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("BadRequest: {} for {}", ex.getMessage(), request.getRequestURI());
        return buildResponseForPath(request.getRequestURI(), ex.getMessage());
    }
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Object handleNotFound(NotFoundException ex, HttpServletRequest request) {
        log.warn("NotFound: {} for {}", ex.getMessage(), request.getRequestURI());
        return buildResponseForPath(request.getRequestURI(), ex.getMessage());
    }
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleOther(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {}: ", request.getRequestURI(), ex);
        return buildResponseForPath(request.getRequestURI(), "Внутренняя ошибка сервера");
    }
    private Object buildResponseForPath(String uri, String message) {
        if (uri == null) {
            return new IndexingResponse(false, message);
        }
        String path = uri.toLowerCase();

        if (path.startsWith("/api/search")) {
            // SearchResponse имеет конструктор (boolean result, String error)
            return new SearchResponse(false, message);
        }

        if (path.startsWith("/api/statistics")) {
            StatisticsResponse resp = new StatisticsResponse();
            resp.setResult(false);
            // statistics поле можно оставить пустым или null — клиент должен понять, что result=false
            return resp;
        }
        if (path.startsWith("/api/startindexing") ||
                path.startsWith("/api/stopindexing") ||
                path.startsWith("/api/indexpage")) {
            return new IndexingResponse(false, message);
        }
        return new IndexingResponse(false, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String message = "Отсутствует обязательный параметр: " + ex.getParameterName();
        return buildResponseForPath(uri, message);
    }
}
