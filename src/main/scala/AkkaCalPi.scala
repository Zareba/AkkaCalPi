package main.scala
 
import scala.math.BigDecimal

case object CalPiShutdown
case class CalPiStart(nrOfParts: Int, partSize: Int)
case class CalPiPartsInfo(startPart: Int, nrOfParts: Int)
case class CalPiPartsResult(result: BigDecimal)

object AkkaCalPi extends App {
    
    calPi(300, 100000)

    import com.typesafe.config.ConfigFactory
    import akka.actor.{ Props, Actor, ActorRef, ActorSystem }
    
    class SumPiPartsWorker(master: ActorRef, nrOfParts: Int) extends Actor {
        var pi = BigDecimal(0)
        var summedParts = 0

        def receive = {
            case CalPiPartsResult(part) => 
                pi += part
                summedParts += 1
                println("%s CalPiPartsResult returned %s".format(summedParts, part))
                if (summedParts == nrOfParts) {
                    println("PI: %s".format(pi))
                    master ! CalPiShutdown
                }
        }
    }

    class CalPiMaster extends Actor {
        // Path to server shutdown actor named calPiPartsListener
        val remoteListener = context.system.actorFor("akka://CalPiPartsServer@10.0.1.35:2552/user/calPiPartsListener")
        // Path to server actor named calPiPartsRouter, that route the work
        val remoteWorkers = context.system.actorFor("akka://CalPiPartsServer@10.0.1.35:2552/user/calPiPartsRouter")

        def receive = {
            case CalPiStart(nrOfParts, partSize) => 
                // Create actor named calPiPartsReceiveSum, for handling sum results from the server
                val receiveRemoteRes = context.actorOf(Props(new SumPiPartsWorker(context.self, nrOfParts)), "calPiPartsReceiveSum")
                // Send the calculation requests to the server
                for (i <- 0 until nrOfParts)
                    remoteWorkers ! CalPiPartsInfo(i * partSize, partSize)
            case CalPiShutdown =>
                println("Shutdown")
                // Tells the server to shutdown
                remoteListener ! CalPiShutdown
                // Shutdown the CalPiClient actor system
                context.system.shutdown
        }
    }
    
    def calPi(nrOfParts: Int, partSize: Int) = {
        // Create actor system names CalPiClient
        val system = ActorSystem("CalPiClient", ConfigFactory.load.getConfig("calPiClient"))
        // Start master actor named calPiMaster
        val master = system.actorOf(Props[CalPiMaster], "calPiMaster")
        
        // Tell the master actor to start the calculation
        master ! CalPiStart(nrOfParts, partSize)
    }
}
