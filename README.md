java-simple-event
=================

super simple event framework inspired from disruptor

Ever digged into the disruptor, a conclusion:
The key is just the ordinal id of ring buffer. disruptor call the id as sequence.
No matter do Publisher publish, or do Dispatcher dispatch, each action both need pass the id.
That's all.

