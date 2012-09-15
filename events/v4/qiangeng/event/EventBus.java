package qiangeng.event;

import static qiangeng.event.ShareLongArray.shareAddress;

public class EventBus extends AbstractEventBus {
	private ThreadGroup threadGroup;

	public EventBus(int bufferSizeBits) {
		super(bufferSizeBits);
		threadGroup = new ThreadGroup("EventBus");
	}

	public ThreadGroup getThreadGroup() {
		return threadGroup;
	}

	public Publisher dispatch(final Dispatcher... dispatchers) {
		int length = dispatchers.length;
		createArray(1 + length);
		long firstAddress = shareAddress(0);
		for (int i = 0; i < length; i++) {
			Thread thread = createDispatchThread(threadGroup, firstAddress, i, dispatchers[i]);
			thread.start();
		}
		return createSinglePublisher(0, length);
	}

	public Publisher[] dispatch(int groups, final Dispatcher... dispatchers) {
		int length = dispatchers.length;
		int groupLength = 1 + length;
		Publisher[] publishers = new Publisher[groups];
		int first = 0;
		int last = length;
		for (int i = 0; i < groups; i++) {
			publishers[i] = createSinglePublisher(first, last);
			first = last + 1;
			last += groupLength;
		}
		createArrayAndDispatchers(groups, length, groupLength, dispatchers);
		return publishers;
	}

	public Publisher dispatchBalance(int groups, final Dispatcher... dispatchers) {
		int length = dispatchers.length;
		int groupLength = 1 + length;
		Publisher publisher = createSharePublisher(0, groups, groupLength);
		createArrayAndDispatchers(groups, length, groupLength, dispatchers);
		return publisher;
	}

	protected void createArrayAndDispatchers(int groups, int length, int groupLength, final Dispatcher... dispatchers) {
		createArray(groups * groupLength);
		for (int i = 0; i < length; i++) {
			Thread thread = createDispatchThread(threadGroup, i, dispatchers[i], groups, groupLength);
			thread.start();
		}
	}

	public Publisher dispatchEqually(int groups, final Dispatcher... dispatchers) {
		int length = dispatchers.length;
		int groupLength = 1 + length;
		Publisher publisher = createEquallyPublisher(0, groups, groupLength);
		createArrayAndDispatchers(groups, length, groupLength, dispatchers);
		return publisher;
	}
}
