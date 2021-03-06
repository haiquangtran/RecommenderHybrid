package org.template.ecommercerecommendation

import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params
import io.prediction.data.storage.Event
import io.prediction.data.store.PEventStore

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

case class DataSourceParams(appName: String) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {

    // create a RDD of (entityID, User)
    val usersRDD: RDD[(String, User)] = getUsers(sc)
    // create a RDD of (entityID, Item)
    val itemsRDD: RDD[(String, Item)] = getItems(sc)
    val eventsRDD: RDD[Event] = getAllEvents(sc)
    val likeEventsRDD: RDD[LikeEvent] = getLikeEvents(eventsRDD)
    val dislikeEventsRDD: RDD[DislikeEvent] = getDislikeEvents(eventsRDD)

    new TrainingData(
      users = usersRDD,
      items = itemsRDD,
      likeEvents = likeEventsRDD,
      dislikeEvents = dislikeEventsRDD
    )

  }

  def getAllEvents(sc: SparkContext): RDD[Event] = {
    val eventsRDD: RDD[Event] = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(List("like", "dislike")),
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("item")))(sc)
      .cache()

      eventsRDD
  }

  def getLikeEvents(eventsRDD: RDD[Event]): RDD[LikeEvent] = {
    val likeEventsRDD: RDD[LikeEvent] = eventsRDD
      .filter { event => event.event == "like" }
      .map { event =>
        try {
          LikeEvent(
            user = event.entityId,
            item = event.targetEntityId.get,
            rating = 1.0,
            t = event.eventTime.getMillis
          )
        } catch {
          case e: Exception =>
            logger.error(s"Cannot convert ${event} to LikeEvent." +
              s" Exception: ${e}.")
            throw e
        }
      }

    likeEventsRDD
  }

  def getDislikeEvents(eventsRDD: RDD[Event]): RDD[DislikeEvent] = {
    val dislikeEventsRDD: RDD[DislikeEvent] = eventsRDD
      .filter { event => event.event == "dislike" }
      .map { event =>
        try {
          DislikeEvent(
            user = event.entityId,
            item = event.targetEntityId.get,
            // Treat as positive for dislike algorithm since we filter out highest scores at end
            rating = 1.0, 
            t = event.eventTime.getMillis
          )
        } catch {
          case e: Exception =>
            logger.error(s"Cannot convert ${event} to DislikeEvent." +
              s" Exception: ${e}.")
            throw e
        }
      }

    dislikeEventsRDD
  }

  def getItems(sc: SparkContext): RDD[(String, Item)] = {
    val itemsRDD: RDD[(String, Item)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "item"
    )(sc).map { case (entityId, properties) =>
      val item = try {
        // Assume categories is optional property of item.
        Item(
          categories = properties.getOpt[List[String]]("categories"),
          name = properties.get[String]("name"),
          price = properties.get[Double]("price"),
          likes = properties.get[Int]("likes"),
          dislikes = properties.get[Int]("dislikes"),
          average_rating = properties.get[Double]("average_rating")
        )
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get properties ${properties} of" +
            s" item ${entityId}. Exception: ${e}.")
          throw e
        }
      }
      (entityId, item)
    }.cache()

    itemsRDD
  }

  def getUsers(sc: SparkContext): RDD[(String, User)] = {
    val usersRDD: RDD[(String, User)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "user"
    )(sc).map { case (entityId, properties) =>
      val user = try {

        User()
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get properties ${properties} of" +
            s" user ${entityId}. Exception: ${e}.")
          throw e
        }
      }
      (entityId, user)
    }.cache()

    usersRDD
  }

}

case class User()

case class Item(
  categories: Option[List[String]], 
  name: String, 
  price: Double, 
  likes: Int, 
  dislikes: Int,
  average_rating: Double
)

case class LikeEvent(
  user: String, 
  item: String, 
  rating: Double, 
  t: Long
)

case class DislikeEvent(
  user: String, 
  item: String,
  rating: Double,  
  t: Long
)

class TrainingData(
  val users: RDD[(String, User)],
  val items: RDD[(String, Item)],
  val likeEvents: RDD[LikeEvent],
  val dislikeEvents: RDD[DislikeEvent]
) extends Serializable {
  override def toString = {
    s"users: [${users.count()} (${users.take(2).toList}...)]" +
    s"items: [${items.count()} (${items.take(2).toList}...)]" +
    s"likeEvents: [${likeEvents.count()}] (${likeEvents.take(2).toList}...)" +
    s"dislikeEvents: [${dislikeEvents.count()}] (${dislikeEvents.take(2).toList}...)"
  }
}
