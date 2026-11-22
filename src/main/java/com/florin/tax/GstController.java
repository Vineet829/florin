package com.florin.tax;

import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.business.Requires;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gst")
public class GstController {

    public record StatesResponse(boolean success, List<GstStates.State> states) {}

    @GetMapping("/states")
    public StatesResponse states(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new StatesResponse(true, GstStates.places());
    }
}
