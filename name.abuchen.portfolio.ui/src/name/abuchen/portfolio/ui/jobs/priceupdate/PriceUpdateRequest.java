package name.abuchen.portfolio.ui.jobs.priceupdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;

/* package */ class PriceUpdateRequest
{
    private final long timestamp = System.currentTimeMillis();
    
    private final Client client;
    private final Map<Security, SecurityUpdateStatus> statuses;
    private final Set<Security> changedSecurities = ConcurrentHashMap.newKeySet();

    private final boolean includeHistorical;
    private final boolean includeLatest;

    private final List<Security> securities;

    private final AtomicBoolean isDirty = new AtomicBoolean(false);
    private final AtomicBoolean progressDirty = new AtomicBoolean(false);

    public PriceUpdateRequest(Client client, List<Security> securities, boolean includeLatest,
                    boolean includeHistorical)
    {
        this.client = client;
        this.securities = securities;

        this.includeHistorical = includeHistorical;
        this.includeLatest = includeLatest;

        Map<Security, SecurityUpdateStatus> map = new HashMap<>();
        for (Security security : securities)
        {
            map.put(security, new SecurityUpdateStatus(security,
                            new FeedUpdateStatus(includeHistorical ? UpdateStatus.WAITING : UpdateStatus.SKIPPED),
                            new FeedUpdateStatus(includeLatest ? UpdateStatus.WAITING : UpdateStatus.SKIPPED)));
        }
        this.statuses = Collections.unmodifiableMap(map);
    }

    void markDirty()
    {
        isDirty.set(true);
    }

    boolean getAndResetDirty()
    {
        return isDirty.getAndSet(false);
    }

    void markProgressDirty()
    {
        progressDirty.set(true);
    }

    boolean getAndResetProgressDirty()
    {
        return progressDirty.getAndSet(false);
    }

    void markSecurityChanged(Security security)
    {
        if (security != null)
            changedSecurities.add(security);
    }

    private List<Security> drainChangedSecurities()
    {
        if (changedSecurities.isEmpty())
            return Collections.emptyList();

        List<Security> drained = new ArrayList<>(changedSecurities.size());
        for (Iterator<Security> iterator = changedSecurities.iterator(); iterator.hasNext();)
        {
            Security security = iterator.next();
            iterator.remove();
            drained.add(security);
        }

        return drained;
    }

    public Client getClient()
    {
        return client;
    }

    public List<Security> getSecurities()
    {
        return securities;
    }

    public boolean isIncludeHistorical()
    {
        return includeHistorical;
    }

    public boolean isIncludeLatest()
    {
        return includeLatest;
    }

    public SecurityUpdateStatus getStatus(Security security)
    {
        return statuses.get(security);
    }

    public PriceUpdateSnapshot getStatusSnapshot()
    {
        return new PriceUpdateSnapshot(timestamp, statuses, drainChangedSecurities());
    }
}
