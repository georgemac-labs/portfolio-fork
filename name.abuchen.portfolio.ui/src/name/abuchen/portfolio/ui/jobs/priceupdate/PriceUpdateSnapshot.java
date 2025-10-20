package name.abuchen.portfolio.ui.jobs.priceupdate;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import name.abuchen.portfolio.model.Security;

public class PriceUpdateSnapshot
{
    private final long timestamp;
    private final Map<Security, SecurityUpdateStatus> statuses;
    private final Collection<Security> changedSecurities;

    private final int taskCount;
    private final int completedTaskCount;

    public PriceUpdateSnapshot(long timestamp, Map<Security, SecurityUpdateStatus> statuses,
                    Collection<Security> changedSecurities)
    {
        this.timestamp = timestamp;

        Map<Security, SecurityUpdateStatus> snapshotStatuses = new HashMap<>(statuses.size());

        for (var entry : statuses.entrySet())
        {
            var status = entry.getValue();
            var historicCopy = copyOf(status.getHistoricStatus());
            var latestCopy = copyOf(status.getLatestStatus());
            snapshotStatuses.put(entry.getKey(), new SecurityUpdateStatus(entry.getKey(), historicCopy, latestCopy));
        }

        this.statuses = Collections.unmodifiableMap(snapshotStatuses);
        this.changedSecurities = Collections.unmodifiableSet(asImmutableSet(changedSecurities));

        var count = 0;
        var completed = 0;

        for (var status : snapshotStatuses.values())
        {
            var statusesToCheck = new FeedUpdateStatus[] { status.getHistoricStatus(), status.getLatestStatus() };

            for (var feedStatus : statusesToCheck)
            {
                if (feedStatus.getStatus() != UpdateStatus.SKIPPED)
                {
                    count++;
                    if (feedStatus.getStatus().isTerminal)
                        completed++;
                }
            }
        }

        this.taskCount = count;
        this.completedTaskCount = completed;
    }

    private static Set<Security> asImmutableSet(Collection<Security> changed)
    {
        if (changed == null || changed.isEmpty())
            return Collections.emptySet();

        return new LinkedHashSet<>(changed);
    }

    private static FeedUpdateStatus copyOf(FeedUpdateStatus status)
    {
        var copy = new FeedUpdateStatus(status.getStatus());
        copy.setStatus(status.getStatus(), status.getMessage());
        return copy;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public Optional<FeedUpdateStatus> getHistoricStatus(Security security)
    {
        var status = statuses.get(security);
        if (status == null)
            return Optional.empty();

        return Optional.of(status.getHistoricStatus());
    }

    public Optional<FeedUpdateStatus> getLatestStatus(Security security)
    {
        var status = statuses.get(security);
        if (status == null)
            return Optional.empty();

        return Optional.of(status.getLatestStatus());
    }

    public int getTaskCount()
    {
        return taskCount;
    }

    public int getCompletedTaskCount()
    {
        return completedTaskCount;
    }

    public Collection<Security> getSecurities()
    {
        return statuses.keySet();
    }

    public Collection<Security> getChangedSecurities()
    {
        return changedSecurities;
    }
}
