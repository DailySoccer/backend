package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import play.Logger;
import play.libs.Akka;

import play.api.DefaultApplication;
import play.api.Play;
import play.api.Mode;
import play.api.Application;

import java.io.File;

public class DailySoccerActors {

    //
    // En un principio esto iba a ser un singleton y usabamos nuestro propio ActorSystem, no el de Akka.system().
    //
    // Deciamos entonces:
    //
    // Si el simulador fuera tambien un actor, no nos haria falta esto porque desde 'el mandariamos
    // mensajes al resto de actores a traves del context. Pero no es asi de momento, asi que necesitamos mantener
    // una referencia a nuestros actores en algun sitio. Otra cosa que tambien lo evita es el remoting.
    //
    // El remoting en cualquier caso parece que lo vamos a necesitar, y quiza entonces sea buen momento para levantar
    // nuestro propio ActorSystem.
    //
    static public void init(boolean isWorker) {

        // Aunque no seamos el worker, hay que inicializar los actores para que funcione el simulador. Ahora mismo en
        // los dynos web tambien se esta haciendo esto, lo cual esta mal.
        final ActorRef instantiateConstestsActor = Akka.system().actorOf(Props.create(InstantiateContestsActor.class), "InstantiateConstestsActor");
        final ActorRef optaProcessorActor = Akka.system().actorOf(Props.create(OptaProcessorActor.class), "OptaProcessorActor");
        final ActorRef givePrizesActor = Akka.system().actorOf(Props.create(GivePrizesActor.class), "GivePrizesActor");
        final ActorRef transactionsActor = Akka.system().actorOf(Props.create(TransactionsActor.class), "TransactionsActor");

        if (isWorker) {
            instantiateConstestsActor.tell("Tick", ActorRef.noSender());
            optaProcessorActor.tell("Tick", ActorRef.noSender());
            givePrizesActor.tell("Tick", ActorRef.noSender());
            transactionsActor.tell("Tick", ActorRef.noSender());
        }

        // El sistema de bots solo se inicializa bajo demanda (por ejemplo, desde la zona de admin)
        Akka.system().actorOf(Props.create(BotParentActor.class), "BotParentActor");
    }

    static public void shutdown() {

        // Esto para todos los actores y metodos scheduleados. No es necesario mirar bIsWorker
        // (aunque logeara "Shutdown application default Akka system.")
        Akka.system().shutdown();

        // Hacemos un 'join' para asegurar que no matamos el modelo estando todavia procesando
        Akka.system().awaitTermination();
    }

    static public void main(String[] args) {
        Application application = new DefaultApplication(new File(args[0]), DailySoccerActors.class.getClassLoader(), null, Mode.Prod());
        Play.start(application);
    }
}
