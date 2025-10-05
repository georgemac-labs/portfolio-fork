package name.abuchen.portfolio.ui.views.trades;

import name.abuchen.portfolio.model.Adaptable;
import name.abuchen.portfolio.model.Adaptor;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.snapshot.trades.Trade;
import name.abuchen.portfolio.snapshot.trades.TradeCategory;
import name.abuchen.portfolio.snapshot.trades.TradeTotals;

/**
 * Wrapper element for displaying trades in a flat table with taxonomy
 * grouping. Can represent either a category (taxonomy classification) or an
 * individual trade.
 */
public class TradeElement implements Adaptable
{
    private final TradeCategory category;
    private final Trade trade;
    private final TradeTotals totals;
    private final int sortOrder;

    /**
     * Creates a category element
     */
    public TradeElement(TradeCategory category, int sortOrder)
    {
        this.category = category;
        this.trade = null;
        this.totals = null;
        this.sortOrder = sortOrder;
    }

    /**
     * Creates a trade element
     */
    public TradeElement(Trade trade, int sortOrder)
    {
        this.category = null;
        this.trade = trade;
        this.totals = null;
        this.sortOrder = sortOrder;
    }

    /**
     * Creates a totals element
     */
    public TradeElement(TradeTotals totals, int sortOrder)
    {
        this.category = null;
        this.trade = null;
        this.totals = totals;
        this.sortOrder = sortOrder;
    }

    public boolean isCategory()
    {
        return category != null;
    }

    public boolean isTrade()
    {
        return trade != null;
    }

    public boolean isTotal()
    {
        return totals != null;
    }

    public TradeCategory getCategory()
    {
        return category;
    }

    public Trade getTrade()
    {
        return trade;
    }

    public TradeTotals getTotals()
    {
        return totals;
    }

    public Classification getClassification()
    {
        return category != null ? category.getClassification() : null;
    }

    public int getSortOrder()
    {
        return sortOrder;
    }

    @Override
    public <T> T adapt(Class<T> type)
    {
        if (category != null && type.isAssignableFrom(category.getClass()))
            return type.cast(category);

        if (totals != null && type.isAssignableFrom(totals.getClass()))
            return type.cast(totals);

        return Adaptor.adapt(type, trade);
    }
}
