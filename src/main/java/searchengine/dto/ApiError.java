package searchengine.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class ApiError {
    private boolean result = false;
    private String error;
}
