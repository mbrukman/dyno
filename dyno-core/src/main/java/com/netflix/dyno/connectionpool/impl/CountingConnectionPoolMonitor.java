/*******************************************************************************
 * Copyright 2011 Netflix
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.netflix.dyno.connectionpool.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;

import com.netflix.dyno.connectionpool.ConnectionPoolMonitor;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostConnectionPool;
import com.netflix.dyno.connectionpool.HostConnectionStats;
import com.netflix.dyno.connectionpool.HostGroup;
import com.netflix.dyno.connectionpool.exception.BadRequestException;
import com.netflix.dyno.connectionpool.exception.NoAvailableHostsException;
import com.netflix.dyno.connectionpool.exception.PoolExhaustedException;
import com.netflix.dyno.connectionpool.exception.PoolTimeoutException;
import com.netflix.dyno.connectionpool.exception.TimeoutException;

/**
 * Impl of {@link ConnectionPoolMonitor} using thread safe AtomicLongs
 * @author poberai
 *
 */
public class CountingConnectionPoolMonitor implements ConnectionPoolMonitor {
	
    // Tracking operation level metrics
    private final AtomicLong operationFailureCount  = new AtomicLong();
    private final AtomicLong operationSuccessCount  = new AtomicLong();
    
    // Tracking connection counts
    private final AtomicLong connectionCreateCount  = new AtomicLong();
    private final AtomicLong connectionClosedCount  = new AtomicLong();
    private final AtomicLong connectionCreateFailureCount = new AtomicLong();
    private final AtomicLong connectionBorrowCount  = new AtomicLong();
    private final AtomicLong connectionReturnCount  = new AtomicLong();
    private final AtomicLong operationFailoverCount = new AtomicLong();

    // tracking host activity
    private final AtomicLong hostAddedCount         = new AtomicLong();
    //private final AtomicLong hostRemovedCount       = new AtomicLong();
    private final AtomicLong hostDownCount          = new AtomicLong();
    private final AtomicLong hostReactivatedCount   = new AtomicLong();
    
    private final AtomicLong poolTimeoutCount      = new AtomicLong();
    private final AtomicLong poolExhastedCount      = new AtomicLong();
    private final AtomicLong operationTimeoutCount  = new AtomicLong();
    private final AtomicLong socketTimeoutCount     = new AtomicLong();
    private final AtomicLong noHostsCount           = new AtomicLong();
    private final AtomicLong unknownErrorCount      = new AtomicLong();
    private final AtomicLong badRequestCount        = new AtomicLong();

    private final ConcurrentHashMap<Host, HostConnectionStats> hostStats = new ConcurrentHashMap<Host, HostConnectionStats>();
    
    public CountingConnectionPoolMonitor() {
    }
    
    private void trackError(Host host, Exception reason) {
    	if (reason != null) {
    		if (reason instanceof PoolTimeoutException) {
    			this.poolTimeoutCount.incrementAndGet();
    		} else if (reason instanceof PoolExhaustedException) {
        			this.poolExhastedCount.incrementAndGet();
    		} else if (reason instanceof TimeoutException) {
    			this.socketTimeoutCount.incrementAndGet();
    		} else if (reason instanceof BadRequestException) {
    			this.badRequestCount.incrementAndGet();
    		} else if (reason instanceof NoAvailableHostsException ) {
    			this.noHostsCount.incrementAndGet();
    		} else {
    			this.unknownErrorCount.incrementAndGet();
    		}
    	} else {
    		this.unknownErrorCount.incrementAndGet();
    	}
    	
        if (host != null) {
        	getOrCreateHostStats(host).opFailure.incrementAndGet();
        }
    }

    @Override
    public void incOperationFailure(Host host, Exception reason) {
        this.operationFailureCount.incrementAndGet();
        trackError(host, reason);
    }

    public long getOperationFailureCount() {
        return this.operationFailureCount.get();
    }

    @Override
    public void incOperationSuccess(Host host, long latency) {
        this.operationSuccessCount.incrementAndGet();
        getOrCreateHostStats(host).opSuccess.incrementAndGet();
    }

    public long getOperationSuccessCount() {
        return this.operationSuccessCount.get();
    }

    @Override
    public void incConnectionCreated(Host host) {
        this.connectionCreateCount.incrementAndGet();
        getOrCreateHostStats(host).created.incrementAndGet();
    }

    public long getConnectionCreatedCount() {
        return this.connectionCreateCount.get();
    }

    @Override
    public void incConnectionClosed(Host host, Exception reason) {
        this.connectionClosedCount.incrementAndGet();
        getOrCreateHostStats(host).closed.incrementAndGet();
    }

    public long getConnectionClosedCount() {
        return this.connectionClosedCount.get();
    }

    @Override
    public void incConnectionCreateFailed(Host host, Exception reason) {
        this.connectionCreateFailureCount.incrementAndGet();
        getOrCreateHostStats(host).createFailed.incrementAndGet();
    }

    public long getConnectionCreateFailedCount() {
        return this.connectionCreateFailureCount.get();
    }

    @Override
    public void incConnectionBorrowed(Host host, long delay) {
        this.connectionBorrowCount.incrementAndGet();
        if (host == null || (host instanceof HostGroup)) {
        	return;
        }
        getOrCreateHostStats(host).borrowed.incrementAndGet();
    }

    public long getConnectionBorrowedCount() {
        return this.connectionBorrowCount.get();
    }

    @Override
    public void incConnectionReturned(Host host) {
        this.connectionReturnCount.incrementAndGet();
        if (host == null || (host instanceof HostGroup)) {
        	return;
        }
        getOrCreateHostStats(host).returned.incrementAndGet();
    }

    public long getConnectionReturnedCount() {
        return this.connectionReturnCount.get();
    }

    public long getPoolExhaustedTimeoutCount() {
        return this.poolExhastedCount.get();
    }

    @Override
    public long getSocketTimeoutCount() {
        return this.socketTimeoutCount.get();
    }
    
    public long getOperationTimeoutCount() {
        return this.operationTimeoutCount.get();
    }

    @Override
    public void incFailover(Host host, Exception reason) {
        this.operationFailoverCount.incrementAndGet();
    }

    @Override
    public long getFailoverCount() {
        return this.operationFailoverCount.get();
    }

    @Override
    public long getHostDownCount() {
        return this.hostDownCount.get();
    }

    @Override
    public long getNoHostCount() {
        return this.noHostsCount.get();
    }

    @Override
    public long getUnknownErrorCount() {
        return this.unknownErrorCount.get();
    }

    @Override
    public long getBadRequestCount() {
        return this.badRequestCount.get();
    }

    public long getNumBusyConnections() {
        return this.connectionBorrowCount.get() - this.connectionReturnCount.get();
    }

    public long getNumOpenConnections() {
        return this.connectionCreateCount.get() - this.connectionClosedCount.get();
    }
    
    @Override
    public long getHostCount() {
        return hostStats.keySet().size();
    }

    public String toString() {
        // Build the complete status string
        return new StringBuilder()
                .append("CountingConnectionPoolMonitor(")
                .append("Connections[" )
                    .append( "open="       ).append(getNumOpenConnections())
                    .append(",busy="       ).append(getNumBusyConnections())
                    .append(",create="     ).append(connectionCreateCount.get())
                    .append(",close="      ).append(connectionClosedCount.get())
                    .append(",failed="     ).append(connectionCreateFailureCount.get())
                    .append(",borrow="     ).append(connectionBorrowCount.get())
                    .append(",return="     ).append(connectionReturnCount.get())
                .append("], Operations[")
                    .append( "success="    ).append(operationSuccessCount.get())
                    .append(",failure="    ).append(operationFailureCount.get())
                    .append(",optimeout="  ).append(operationTimeoutCount.get())
                    .append(",timeout="    ).append(socketTimeoutCount.get())
                    .append(",failover="   ).append(operationFailoverCount.get())
                    .append(",nohosts="    ).append(noHostsCount.get())
                    .append(",unknown="    ).append(unknownErrorCount.get())
                    .append(",exhausted="  ).append(poolExhastedCount.get())
                .append("], Hosts[")
                    .append( "add="        ).append(hostAddedCount.get())
                    .append(",down="       ).append(hostDownCount.get())
                    .append(",reactivate=" ).append(hostReactivatedCount.get())
                .append("])").toString();
    }

	@Override
	public long getHostUpCount() {
		int count = 0;
		for (HostConnectionStats stats : hostStats.values()) {
			count = stats.isHostUp() ? count + 1 : count;
		}
		return count;
	}

	@Override
	public void hostAdded(Host host, HostConnectionPool<?> pool) {
		hostStats.putIfAbsent(host, new HostConnectionStatsImpl(host));
	}

	@Override
	public void hostRemoved(Host host) {
		getOrCreateHostStats(host).hostUp.set(false);
	}

	@Override
	public void hostDown(Host host, Exception reason) {
		getOrCreateHostStats(host).hostUp.set(false);
	}

	@Override
	public void hostUp(Host host, HostConnectionPool<?> pool) {
		getOrCreateHostStats(host).hostUp.set(true);
	}

	@Override
	public Map<Host, HostConnectionStats> getHostStats() {
		return hostStats;
	}
	
	public HostConnectionStatsImpl getOrCreateHostStats(Host host) {
		
		HostConnectionStatsImpl hStats = (HostConnectionStatsImpl) hostStats.get(host);
		if (hStats != null) {
			return hStats;
		}
		hostStats.putIfAbsent(host, new HostConnectionStatsImpl(host));
		return (HostConnectionStatsImpl) hostStats.get(host);
	}
	
	private class HostConnectionStatsImpl implements HostConnectionStats {

		private AtomicBoolean hostUp = new AtomicBoolean(true);
		private final String name;

		private final AtomicLong opFailure  = new AtomicLong();
		private final AtomicLong opSuccess  = new AtomicLong();
		private final AtomicLong created  = new AtomicLong();
		private final AtomicLong closed  = new AtomicLong();
		private final AtomicLong createFailed = new AtomicLong();
		private final AtomicLong borrowed  = new AtomicLong();
		private final AtomicLong returned  = new AtomicLong();
		    
		private HostConnectionStatsImpl(Host host) {
			this.name = host.getHostName();
		}
		
		@Override
		public boolean isHostUp() {
			return hostUp.get();
		}

		@Override
		public long getConnectionsBorrowed() {
			return borrowed.get();
		}

		@Override
		public long getConnectionsReturned() {
			return returned.get();
		}

		@Override
		public long getConnectionsCreated() {
			return created.get();
		}

		@Override
		public long getConnectionsClosed() {
			return closed.get();
		}

		@Override
		public long getConnectionsCreateFailed() {
			return createFailed.get();
		}

		@Override
		public long getOperationSuccessCount() {
			return opSuccess.get();
		}

		@Override
		public long getOperationErrorCount() {
			return opFailure.get();
		}
		
		public String toString() {
			return name + " isUp: " + hostUp.get() + 
					", borrowed: " + borrowed.get() + 
					", returned: " + returned.get() + 
					", created: " + created.get() + 
					", closed: " + closed.get() + 
					", createFailed: " + createFailed.get() + 
					", success: " + opSuccess.get() + 
					", error: " + opFailure.get(); 
		}
	}
	
	public static class UnitTest {
		
		
		@Test
		public void testProcess() throws Exception {
			
			CountingConnectionPoolMonitor counter = new CountingConnectionPoolMonitor();
			
			Host host1 = new Host("host1", 1111);
			Host host2 = new Host("host2", 2222);
			
			// Host 1
			counter.incConnectionCreated(host1);
			counter.incConnectionClosed(host1, null);
			counter.incConnectionCreateFailed(host1, null);
			counter.incConnectionBorrowed(host1, 0);
			counter.incConnectionReturned(host1);
			
			counter.incOperationSuccess(host1, 0);
			counter.incOperationFailure(host1, null);
			
			// Host 2
			counter.incConnectionBorrowed(host2, 0);
			counter.incConnectionReturned(host2);
			
			counter.incOperationSuccess(host2, 0);
			counter.incOperationFailure(host2, null);
			
			counter.incOperationFailure(host2, new PoolTimeoutException(""));
			counter.incOperationFailure(host2, new PoolExhaustedException(""));
			counter.incOperationFailure(host2, new NoAvailableHostsException(""));
			
			// VERIFY COUNTS
			Assert.assertEquals(1, counter.getConnectionCreatedCount());
			Assert.assertEquals(1, counter.getConnectionClosedCount());
			Assert.assertEquals(1, counter.getConnectionCreateFailedCount());
			Assert.assertEquals(2, counter.getConnectionBorrowedCount());
			Assert.assertEquals(2, counter.getConnectionReturnedCount());
			
			Assert.assertEquals(2, counter.getOperationSuccessCount());
			Assert.assertEquals(5, counter.getOperationFailureCount());

			Assert.assertEquals(1, counter.getHostStats().get(host1).getConnectionsBorrowed());
			Assert.assertEquals(1, counter.getHostStats().get(host1).getConnectionsReturned());
			Assert.assertEquals(1, counter.getHostStats().get(host1).getConnectionsCreated());
			Assert.assertEquals(1, counter.getHostStats().get(host1).getConnectionsCreateFailed());
			Assert.assertEquals(1, counter.getHostStats().get(host1).getOperationSuccessCount());
			Assert.assertEquals(1, counter.getHostStats().get(host1).getOperationErrorCount());

			Assert.assertEquals(1, counter.getHostStats().get(host2).getConnectionsBorrowed());
			Assert.assertEquals(1, counter.getHostStats().get(host2).getConnectionsReturned());
			Assert.assertEquals(0, counter.getHostStats().get(host2).getConnectionsCreated());
			Assert.assertEquals(0, counter.getHostStats().get(host2).getConnectionsCreateFailed());
			Assert.assertEquals(1, counter.getHostStats().get(host2).getOperationSuccessCount());
			Assert.assertEquals(4, counter.getHostStats().get(host2).getOperationErrorCount());
		}
	}
}
