package viper.gobra.util

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object DefaultGobraExecutionContext {
  val minimalThreadPoolSize: Int = 1
}

class DefaultGobraExecutionContext(val threadPoolSize: Int = Math.max(DefaultGobraExecutionContext.minimalThreadPoolSize, Runtime.getRuntime.availableProcessors()),
                                   threadNamePrefix: String = "thread") extends GobraExecutionContext {
  // this is quite redundant to the code in ViperServer but since Gobra should not have a dependency on ViperServer,
  // there is no other way than to duplicate the code
  protected lazy val threadStackSize: Long = 128L * 1024L * 1024L // 128M seems to consistently be recommended by Silicon and Carbon
  protected lazy val service: ExecutorService = Executors.newFixedThreadPool(
    threadPoolSize, new ThreadFactory() {

      import java.util.concurrent.atomic.AtomicInteger

      private val mCount = new AtomicInteger(1)
      override def newThread(runnable: Runnable): Thread = {
        val threadName = s"$threadNamePrefix-${mCount.getAndIncrement()}"
        new Thread(null, runnable, threadName, threadStackSize)
      }
    })

  private lazy val context: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(service)

  override def execute(runnable: Runnable): Unit = context.execute(runnable)

  override def reportFailure(cause: Throwable): Unit = context.reportFailure(cause)
}
