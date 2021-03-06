package org.template.ecommercerecommendation

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

case class Query(
  user: String,
  num: Int,
  items: Option[Set[String]],
  // categories are the content attributes
  categories: Option[Set[String]],
  whiteList: Option[Set[String]],
  blackList: Option[Set[String]],
  positivePreferences: Option[Set[String]],
  negativePreferences: Option[Set[String]],
  minPrice: Option[Double],
  maxPrice: Option[Double],
  popular: Option[Boolean]
) extends Serializable

case class PredictedResult(
  itemScores: Array[ItemScore]
) extends Serializable

case class ItemScore(
  name: String,
  item: String,
  score: Double,
  categories: Option[List[String]],
  price: Double,
  likes: Int,
  dislikes: Int,
  average_rating: Double
) extends Serializable

object WotmRecommendationEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("like_als" -> classOf[LikeAlsAlgorithm], "dislike_als" -> classOf[DislikeAlsAlgorithm]),
      classOf[Serving])
  }
}
