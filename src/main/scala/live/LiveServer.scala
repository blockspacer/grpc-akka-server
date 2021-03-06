package live

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.ConfigFactory
import live.interfaces.{Grpc, Ws}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object LiveServer extends App {

  val env = sys.env.getOrElse("ENV", "dev")
  val config = ConfigFactory.load(env)
  implicit val system: ActorSystem = ActorSystem("live", config)
  val cluster: Cluster = Cluster(system)
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  val log = system.log

  Try(LiveServer.run()) match {
    case Failure(exception) =>
      log.error(exception, "Shutting down due to error")
      system.terminate()
    case Success(_) =>
  }

  sys.addShutdownHook {
    log.info("Shutting down system due to shutdown signal")
    cluster.leave(cluster.selfAddress)
  }

  private def run(): Unit = {
    startClusterFormation()
    val grpcPort = sys.env.getOrElse("GRPC_PORT", "8080").toInt
    val wsPort = sys.env.getOrElse("WS_PORT", "9090").toInt
    start(
      Grpc(grpcPort),
      Ws(wsPort)
    )
  }

  private def start(interfaces: Interface*): Unit = {
    interfaces.foreach { interface =>
      val binding = interface.up
      binding.foreach { binding =>
        log.info(s"${interface.name} interface bound to: ${binding.localAddress}")
      }
    }
  }

  private def startClusterFormation(): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    cluster.registerOnMemberUp(log.info("Member is up!"))
  }
}