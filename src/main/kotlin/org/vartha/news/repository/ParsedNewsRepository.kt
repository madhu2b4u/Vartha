package org.vartha.news.repository

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vartha.news.model.ParsedNews
import org.vartha.news.model.ParsedNewsCategory

@Repository
interface ParsedNewsRepository : MongoRepository<ParsedNews, String> {
    fun findByCategory(category: String): MutableList<ParsedNews>

    @Query(value = "{}", fields = "{ 'category': 1, '_id': 0 }")
    fun findAllCategories(): List<ParsedNewsCategory>
}
