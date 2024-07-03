package org.vartha.news.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.vartha.news.model.ParsedNews
import org.vartha.news.model.UpdateRequest
import org.vartha.news.repository.ParsedNewsRepository
import java.util.*

@RestController
@RequestMapping("/parsedNews")
class ParsedNewsController(
    @Autowired private val parsedNewsRepository: ParsedNewsRepository,
    private val parsedNews: ParsedNews
) {
    @get:GetMapping
    val finaAll: MutableList<ParsedNews> get() = parsedNewsRepository.findAll()

    @GetMapping("/fetchCategory")
    fun fetchCategory(@RequestParam category: String): MutableList<ParsedNews> {
        return parsedNewsRepository.findByCategory(category)
    }

    @GetMapping("/fetchCategories")
    fun fetchCategories(): List<String> {
        val categories = parsedNewsRepository.findAllCategories()
        return categories.map { it.category }.distinct()
    }

    @PutMapping("/{id}")
    fun updateCharacter(@PathVariable id: String, @RequestBody updateRequest: UpdateRequest): ResponseEntity<ParsedNews> {
        val existingNews: Optional<ParsedNews> = parsedNewsRepository.findById(id)
        if (existingNews.isEmpty) {
            return ResponseEntity.notFound().build()
        }
        val newsToUpdate = existingNews.get().apply {
            this.summary = updateRequest.summary
        }
        return ResponseEntity.ok(parsedNewsRepository.save(newsToUpdate))
    }
}
