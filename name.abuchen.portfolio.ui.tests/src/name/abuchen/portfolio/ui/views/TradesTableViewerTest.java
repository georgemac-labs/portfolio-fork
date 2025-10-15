package name.abuchen.portfolio.ui.views;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.IsCloseTo.closeTo;

import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.junit.AccountBuilder;
import name.abuchen.portfolio.junit.PortfolioBuilder;
import name.abuchen.portfolio.junit.SecurityBuilder;
import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.trades.Trade;
import name.abuchen.portfolio.snapshot.trades.TradeCollector;
import name.abuchen.portfolio.ui.views.trades.TradeElement;

@SuppressWarnings("nls")
public class TradesTableViewerTest
{
    @Test
    public void tradeReturnIsNotWeightedWhenGroupedByTaxonomy() throws Exception
    {
        Client client = new Client();

        Security security = new SecurityBuilder() //
                        .addPrice("2020-01-01", Values.Quote.factorize(100)) //
                        .addPrice("2020-02-01", Values.Quote.factorize(110)) //
                        .addTo(client);

        Account account = new AccountBuilder() //
                        .deposit_("2020-01-01", Values.Amount.factorize(20000)) //
                        .addTo(client);

        new PortfolioBuilder(account) //
                        .buy(security, "2020-01-01", Values.Share.factorize(100), Values.Amount.factorize(10000)) //
                        .sell(security, "2020-02-01", Values.Share.factorize(100), Values.Amount.factorize(11000)) //
                        .addTo(client);

        TradeCollector collector = new TradeCollector(client, new TestCurrencyConverter());
        List<Trade> trades = collector.collect(security);

        Trade trade = trades.get(0);
        TradeElement element = new TradeElement(trade, 0, 0.5);

        double expected = trade.getReturn();

        assertThat(expected, closeTo(0.1, 0.0000001));
        assertThat(TradesTableViewer.getReturnValue(element), closeTo(expected, 0.0000001));
        assertThat(TradesTableViewer.getReturnValue(trade), closeTo(expected, 0.0000001));
    }
}
