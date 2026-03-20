package name.abuchen.portfolio.ui.views;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import name.abuchen.portfolio.junit.AccountBuilder;
import name.abuchen.portfolio.junit.PortfolioBuilder;
import name.abuchen.portfolio.junit.SecurityBuilder;
import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.security.LazySecurityPerformanceRecord;
import name.abuchen.portfolio.snapshot.security.LazySecurityPerformanceSnapshot;
import name.abuchen.portfolio.util.Interval;

@SuppressWarnings("nls")
public class SecuritiesPerformanceViewTaxonomyAggregationTest
{
    @Test
    public void taxonomyBranchAggregateAppliesAssignmentWeight() throws Exception
    {
        Client client = new Client();
        client.setBaseCurrency(CurrencyUnit.EUR);

        Security security = new SecurityBuilder() //
                        .addPrice("2020-01-01", Values.Quote.factorize(100)) //
                        .addPrice("2020-01-02", Values.Quote.factorize(100)) //
                        .addTo(client);

        var account = new AccountBuilder() //
                        .deposit_("2020-01-01", Values.Amount.factorize(2000)) //
                        .addTo(client);

        new PortfolioBuilder(account) //
                        .buy(security, "2020-01-01", Values.Share.factorize(10), Values.Amount.factorize(1000)) //
                        .addTo(client);

        var snapshot = LazySecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        Interval.of(LocalDate.parse("2020-01-01"), LocalDate.parse("2020-01-02")));

        LazySecurityPerformanceRecord record = snapshot.getRecords().stream().filter(r -> r.getSecurity() == security)
                        .findFirst().orElseThrow();

        Money fullMarketValue = record.getMarketValue().get();

        Object weightedAggregate = newAggregateRow(List.of(record), Map.of(security, Classification.ONE_HUNDRED_PERCENT / 2));

        Money weightedMarketValue = sumMarketValue(weightedAggregate, client.getBaseCurrency());

        assertThat(weightedMarketValue, is(Money.of(client.getBaseCurrency(), fullMarketValue.getAmount() / 2)));
    }

    private static Object newAggregateRow(List<LazySecurityPerformanceRecord> records, Map<Security, Integer> weights)
                    throws Exception
    {
        Class<?> aggregateType = Class.forName("name.abuchen.portfolio.ui.views.SecuritiesPerformanceView$AggregateRow");
        Constructor<?> ctor = aggregateType.getDeclaredConstructor(List.class, Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(records, weights);
    }

    @SuppressWarnings("unchecked")
    private static Money sumMarketValue(Object aggregate, String currencyCode) throws Exception
    {
        Method sum = aggregate.getClass().getDeclaredMethod("sum", String.class, Function.class);
        sum.setAccessible(true);
        return (Money) sum.invoke(aggregate, currencyCode,
                        (Function<LazySecurityPerformanceRecord, Money>) r -> r.getMarketValue().get());
    }
}
