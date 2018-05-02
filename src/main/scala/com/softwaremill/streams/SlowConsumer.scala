package com.softwaremill.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

import scala.concurrent.Await
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.{Process, async, time}

object AkkaSlowConsumer extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  try {
    val future = Source.tick(0.millis, 100.millis, 1)
      .conflate(identity)(_ + _)
      .runForeach { el =>
        Thread.sleep(1000L)
        println(el)
      }

    Await.result(future, 1.hour)
  } finally system.terminate()
}

object ScalazSlowConsumer extends App {
  implicit val scheduler = Strategy.DefaultTimeoutScheduler

  val queue = async.boundedQueue[Int](10000)
  val enqueueProcess = time.awakeEvery(100.millis)
    .map(_ => 1)
    .to(queue.enqueue)
  val dequeueProcess = queue.dequeueAvailable
    .map(_.sum)
    .flatMap(el => Process.eval_(Task {
      Thread.sleep(1000L)
      println(el)
    }))

  (enqueueProcess merge dequeueProcess).run.run
  println("I'm slow")
  println('I'm slow and bad')
}
