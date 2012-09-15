package qiangeng.event;

/**
 * NOTE: the {@link #next()} and {@link #publish(long)} method not thread safe.
 * 
 * If a {@link Publisher} used in a multiple threaded environment, the call must ensure the synchronize feature, it's so
 * easy:
 * 
 * <pre>
 * synchronize(publisher){
 * 	id=publisher.next();
 * 	buffer.set(id, eventObject);
 * 	pubisher.publish(id);
 * }
 * </pre>
 * 
 * or lock
 * 
 * <pre>
 * try (Blocking block = lock(publisher)) {
 * 	id = publisher.next();
 * 	buffer.set(id, eventObject);
 * 	pubisher.publish(id);
 * }
 * </pre>
 * 
 * @author qinxian
 * 
 */
public abstract class Publisher {
	abstract public long next();

	abstract public void publish(long endOrdinal);
}
