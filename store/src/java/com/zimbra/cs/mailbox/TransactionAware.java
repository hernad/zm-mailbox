/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2018 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.cs.mailbox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.cache.ThreadLocalCache;
import com.zimbra.cs.mailbox.cache.ThreadLocalCacheManager;
import com.zimbra.cs.mailbox.cache.ThreadLocalCache.CachedObject;
import com.zimbra.cs.mailbox.cache.ThreadLocalCache.GreedyCachedObject;

public abstract class TransactionAware<V, C extends TransactionAware.Change> {

    private String name;
    private TransactionCacheTracker tracker;
    private final ThreadLocalCache<Changes<C>, CachedChanges<C>> threadChanges;
    private final Getter<V,?> transactionAccessor;

    public TransactionAware(String name, TransactionCacheTracker cacheTracker, Getter<V,?> getter) {
        this.tracker = cacheTracker;
        this.name = name;
        this.threadChanges = ThreadLocalCacheManager.getInstance().newThreadLocalCache(name, "CHANGES");
        this.transactionAccessor = getter;
    }

    public String getName() {
        return name;
    }

    public boolean isInTransaction() {
        return tracker.isInTransaction();
    }

    protected V data() {
        return transactionAccessor != null ? transactionAccessor.getObject(this) : null;
    }

    public void clearLocalCache() {
        if (transactionAccessor != null) {
            transactionAccessor.clearCache(isInTransaction());
        }
    }

    public boolean hasChanges() {
        return getChanges().hasChanges();
    }

    protected void addChange(C change) {
        getChanges().addChange(change);
    }

    public Changes<C> getChanges() {
        return threadChanges.get(isInTransaction(), new Callable<CachedChanges<C>>() {

            @Override
            public CachedChanges<C> call() throws Exception {
                tracker.addToTracker(TransactionAware.this);
                return new CachedChanges<>(new Changes<>(name));
            }

        }).getObject();
    }

    public List<C> getChangeList() {
        return getChanges().getChanges();
    }

    public void resetChanges() {
        Changes<C> changes = getChanges();
        if (changes.hasChanges()) {
            ZimbraLog.cache.warn("clearing %d uncommitted changes for %s", changes.size(), getName());
        }
        threadChanges.remove(isInTransaction());
    }


    public static enum ChangeType {
        CLEAR, REMOVE, //used by both set and map
        MAP_PUT, MAP_PUT_ALL,
        SET_ADD, SET_ADD_ALL, SET_REMOVE_ALL, SET_RETAIN_ALL,
        LRU_MARK_ACCESSED;
    }

    public static class Changes<C extends Change> {
        private List<C> changes = new ArrayList<>();
        private String objectName;

        public Changes(String objectName) {
            this.objectName = objectName;
            reset();
        }

        public void reset() {
            if (ZimbraLog.cache.isTraceEnabled()) {
                ZimbraLog.cache.trace("resetting changes for %s", getName());
            }
            changes.clear();
        }

        public void addChange(C change) {
            changes.add(change);
        }

        public boolean hasChanges() {
            return !changes.isEmpty();
        }

        public List<C> getChanges() {
            return changes;
        }

        public String getName() {
            return objectName;
        }

        public int size() {
            return changes.size();
        }

        @Override
        public String toString() {
            MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).add("name", getName());
            if (changes.size() > 20) {
                helper.add("changes.size()", changes.size());
            } else {
                helper.add("changes", changes);
            }
            return helper.toString();
        }
    }

    public static abstract class Change {

        private ChangeType changeType;

        public Change(ChangeType changeType) {
            this.changeType = changeType;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        protected ToStringHelper toStringHelper() {
            return MoreObjects.toStringHelper(this);
        }
    }

    protected static abstract class Getter<V, G extends CachedObject<V>> {

        protected String objectName;
        protected ThreadLocalCache<V, G> localCache;

        public Getter(String objectName) {
            this.objectName = objectName;
            localCache = ThreadLocalCacheManager.getInstance().newThreadLocalCache(objectName, "CACHED_VALUES");
        }

        public String getObjectName() {
            return objectName;
        }

        protected abstract G loadCacheValue();

        public V getObject(TransactionAware<?,?> obj) {
            Callable<G> callable = new Callable<G>() {
                @Override
                public G call() throws Exception {
                    G val = loadCacheValue();
                    if (ZimbraLog.cache.isTraceEnabled()) {
                        ZimbraLog.cache.trace("adding threadlocal value for %s for thread %s, cache=%s", this, Thread.currentThread(), localCache);
                    }
                    obj.tracker.addToTracker(obj);
                    return val;
                }
            };
            return localCache.get(obj.isInTransaction(), callable).getObject();
        }

        public void clearCache(boolean inTransaction) {
            localCache.remove(inTransaction);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("obj", objectName).toString();
        }
    }

    protected static abstract class GreedyGetter<V> extends Getter<V, GreedyCachedObject<V>> {

        public GreedyGetter(String objectName) {
            super(objectName);
        }

        protected abstract V loadObject();

        @Override
        protected GreedyCachedObject<V> loadCacheValue() {
            return new GreedyCachedObject<>(objectName, loadObject());
        }
    }

    private static class CachedChanges<C extends Change> extends CachedObject<Changes<C>> {

        private Changes<C> changes;

        public CachedChanges(Changes<C> changes) {
            super(changes.getName());
            this.changes = changes;
        }

        @Override
        public Changes<C> getObject() {
            return changes;
        }

    }
}