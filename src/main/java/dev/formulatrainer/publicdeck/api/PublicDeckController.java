package dev.formulatrainer.publicdeck.api;

import dev.formulatrainer.publicdeck.PublicDeckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/decks")
public class PublicDeckController {

    private final PublicDeckService publicDeckService;

    public PublicDeckController(PublicDeckService publicDeckService) {
        this.publicDeckService = publicDeckService;
    }

    @GetMapping("/{publicId}")
    public PublicDeckResponse get(@PathVariable("publicId") String publicId) {
        return publicDeckService.get(publicId);
    }

}
