package pe.edu.cibertec.ferrestock.authreportes.service;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import static org.assertj.core.api.Assertions.*;

class LoginLimiterTest {
    static final class MutableClock extends Clock {
        final AtomicLong seconds=new AtomicLong(120);public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochSecond(seconds.get());}
    }
    @Test void fifthLoginAcceptedSixthRejected(){var l=new LoginLimiter(new MutableClock());for(int i=0;i<5;i++)l.attempt("login","source"+i);assertThatThrownBy(() -> l.attempt("login","new")).hasMessage("LIMITE_LOGIN");}
    @Test void twentiethOriginAcceptedTwentyFirstRejected(){var l=new LoginLimiter(new MutableClock());for(int i=0;i<20;i++)l.attempt("user"+i,"source");assertThatThrownBy(() -> l.attempt("another","source")).hasMessage("LIMITE_LOGIN");}
    @Test void nextMinuteCleansExpiredCounters(){var clock=new MutableClock();var l=new LoginLimiter(clock);for(int i=0;i<5;i++)l.attempt("login","source");clock.seconds.addAndGet(60);assertThatCode(() -> l.attempt("login","source")).doesNotThrowAnyException();}
    @Test void counterStorageSaturationFailsClosedThenRecovers(){
        var clock=new MutableClock();var l=new LoginLimiter(clock);
        for(int i=0;i<2048;i++)l.attempt("login"+i,"source"+i);
        assertThatThrownBy(() -> l.attempt("overflow","overflow")).hasMessage("LIMITE_LOGIN");
        clock.seconds.addAndGet(60);assertThatCode(() -> l.attempt("overflow","overflow")).doesNotThrowAnyException();
    }
    @Test void concurrentAttemptsNeverExceedFive()throws Exception{
        var l=new LoginLimiter(new MutableClock());var ready=new CountDownLatch(1);var allowed=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(12)){
            var jobs=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<30;i++)jobs.add(pool.submit(() -> {try{ready.await();l.attempt("shared","source");allowed.incrementAndGet();}catch(ApiProblem ignored){}catch(InterruptedException ex){Thread.currentThread().interrupt();}}));
            ready.countDown();for(var job:jobs)job.get(10,TimeUnit.SECONDS);
        }
        assertThat(allowed.get()).isEqualTo(5);
    }
    @Test void fourKdfSlotsReusableAfterRelease(){var l=new LoginLimiter(new MutableClock());for(int i=0;i<4;i++)l.acquire();assertThatThrownBy(l::acquire).hasMessage("LIMITE_LOGIN");l.release();assertThatCode(l::acquire).doesNotThrowAnyException();}
}
