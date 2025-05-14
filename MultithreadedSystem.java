import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

class Task {
    private final int id;
    private final String message;

    public Task(int id, String message) {
        this.id = id;
        this.message = message;
    }

    public void execute() {
        System.out.printf("Processing Task ID: %d | Message: %s | Thread: %s%n",
                          id, message, Thread.currentThread().getName());
        try {
            Thread.sleep(new Random().nextInt(1000));  
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class BlockingTaskQueue {
    private final Queue<Task> queue = new LinkedList<>();
    private final int capacity;
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public BlockingTaskQueue(int capacity) {
        this.capacity = capacity;
    }

    public void put(Task task) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity)
                notFull.await();
            queue.add(task);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public Task take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty())
                notEmpty.await();
            Task task = queue.poll();
            notFull.signal();
            return task;
        } finally {
            lock.unlock();
        }
    }
}

class Producer implements Runnable {
    private final BlockingTaskQueue queue;
    private final int producerId;
    private static int taskCounter = 0;

    public Producer(BlockingTaskQueue queue, int producerId) {
        this.queue = queue;
        this.producerId = producerId;
    }

    @Override
    public void run() {
        Random rand = new Random();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String msg = "Task_from_Producer_" + producerId;
                Task task = new Task(++taskCounter, msg);
                queue.put(task);
                System.out.printf("Produced: %s | Queue Size: %d%n", msg, queueSize(queue));
                Thread.sleep(rand.nextInt(500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private int queueSize(BlockingTaskQueue queue) {
        
        try {
            var field = queue.getClass().getDeclaredField("queue");
            field.setAccessible(true);
            Queue<Task> q = (Queue<Task>) field.get(queue);
            return q.size();
        } catch (Exception e) {
            return -1;
        }
    }
}

class Consumer implements Runnable {
    private final BlockingTaskQueue queue;

    public Consumer(BlockingTaskQueue queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task task = queue.take();
                task.execute();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

public class MultithreadedSystem {
    public static void main(String[] args) throws InterruptedException {
        final int QUEUE_CAPACITY = 10;
        final int NUM_PRODUCERS = 3;
        final int NUM_CONSUMERS = 5;

        BlockingTaskQueue queue = new BlockingTaskQueue(QUEUE_CAPACITY);

        ExecutorService producerPool = Executors.newFixedThreadPool(NUM_PRODUCERS);
        ExecutorService consumerPool = Executors.newFixedThreadPool(NUM_CONSUMERS);

        for (int i = 0; i < NUM_PRODUCERS; i++) {
            producerPool.execute(new Producer(queue, i));
        }

        for (int i = 0; i < NUM_CONSUMERS; i++) {
            consumerPool.execute(new Consumer(queue));
        }

    
        Thread.sleep(10000);

        producerPool.shutdownNow();
        consumerPool.shutdownNow();

        producerPool.awaitTermination(5, TimeUnit.SECONDS);
        consumerPool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("System shut down gracefully.");
    }
}