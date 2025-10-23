package name.abuchen.portfolio.ui.util.searchfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.TransactionPair;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.util.TextUtil;

public class TransactionSearchField extends ControlContribution
{
    private String filterText;
    private Consumer<String> onRecalculationNeeded;

    public TransactionSearchField(Consumer<String> onRecalculationNeeded)
    {
        super("searchbox"); //$NON-NLS-1$
        this.onRecalculationNeeded = onRecalculationNeeded;
    }

    public String getFilterText()
    {
        return filterText;
    }

    @Override
    protected Control createControl(Composite parent)
    {
        final Text search = new Text(parent, SWT.SEARCH | SWT.ICON_CANCEL);
        search.setMessage(Messages.LabelSearch);
        search.setSize(300, SWT.DEFAULT);

        PortfolioPlugin.info(String.format("%s createControl: initialized search field", logPrefix(search)));

        search.addListener(SWT.Selection, event -> {
            logSwtEvent("listener=SWT.Selection", search, event);
            if (event.detail == SWT.CANCEL)
                PortfolioPlugin.info(String.format("%s listener=SWT.Selection detected SWT.CANCEL", logPrefix(search)));
        });
        search.addListener(SWT.DefaultSelection, event -> logSwtEvent("listener=SWT.DefaultSelection", search, event));
        search.addListener(SWT.MouseDown, event -> logSwtEvent("listener=SWT.MouseDown", search, event));
        search.addListener(SWT.MouseUp, event -> logSwtEvent("listener=SWT.MouseUp", search, event));
        search.addListener(SWT.MouseDoubleClick, event -> logSwtEvent("listener=SWT.MouseDoubleClick", search, event));
        search.addListener(SWT.FocusIn, event -> logSwtEvent("listener=SWT.FocusIn", search, event));
        search.addListener(SWT.FocusOut, event -> logSwtEvent("listener=SWT.FocusOut", search, event));
        search.addListener(SWT.KeyDown, event -> logSwtEvent("listener=SWT.KeyDown", search, event));
        search.addListener(SWT.KeyUp, event -> logSwtEvent("listener=SWT.KeyUp", search, event));
        search.addListener(SWT.Traverse, event -> logSwtEvent("listener=SWT.Traverse", search, event));
        search.addListener(SWT.Verify, event -> logSwtEvent("listener=SWT.Verify", search, event));
        search.addListener(SWT.Modify, event -> logSwtEvent("listener=SWT.Modify", search, event));

        // reset filterText when user switch tab
        search.addDisposeListener(e -> {
            String disposeMessage = String.format(
                            "%s disposeListener: widget=%s disposed=%s filterTextBefore=%s", logPrefix(search),
                            e.widget.getClass().getSimpleName(), Boolean.toString(search.isDisposed()),
                            safe(filterText));
            PortfolioPlugin.info(disposeMessage);
            filterText = null;
            String notifyMessage = String.format(
                            "%s disposeListener: notifying recalculation with filterText=%s", logPrefix(search),
                            safe(filterText));
            PortfolioPlugin.info(notifyMessage);
            onRecalculationNeeded.accept(filterText);
        });

        search.addModifyListener(e -> {
            String previousFilterText = filterText;
            var text = search.getText().trim();
            if (text.isEmpty())
            {
                filterText = null;
                logFilterUpdate(search, previousFilterText, text, "empty trimmed text");
            }
            else
            {
                filterText = text.toLowerCase();
                logFilterUpdate(search, previousFilterText, text, "non-empty trimmed text");
            }
            String notifyMessage = String.format(
                            "%s modifyListener: notifying recalculation with filterText=%s", logPrefix(search),
                            safe(filterText));
            PortfolioPlugin.info(notifyMessage);
            onRecalculationNeeded.accept(filterText);
        });

        return search;
    }

    @Override
    protected int computeWidth(Control control)
    {
        return control.computeSize(100, SWT.DEFAULT, true).x;
    }

    public ViewerFilter getViewerFilter(Function<Object, TransactionPair<?>> transaction)
    {
        List<Function<TransactionPair<?>, Object>> searchLabels = new ArrayList<>();
        searchLabels.add(tx -> tx.getTransaction().getSecurity());
        searchLabels.add(tx -> tx.getTransaction().getOptionalSecurity().map(Security::getIsin).orElse(null));
        searchLabels.add(tx -> tx.getTransaction().getOptionalSecurity().map(Security::getWkn).orElse(null));
        searchLabels.add(tx -> tx.getTransaction().getOptionalSecurity().map(Security::getTickerSymbol).orElse(null));
        searchLabels.add(TransactionPair::getOwner);
        searchLabels.add(tx -> tx.getTransaction().getCrossEntry() != null
                        ? tx.getTransaction().getCrossEntry().getCrossOwner(tx.getTransaction())
                        : null);
        searchLabels.add(tx -> tx.getTransaction() instanceof AccountTransaction
                        ? ((AccountTransaction) tx.getTransaction()).getType()
                        : ((PortfolioTransaction) tx.getTransaction()).getType());
        searchLabels.add(tx -> tx.getTransaction().getNote());
        searchLabels.add(tx -> tx.getTransaction().getShares());
        searchLabels.add(tx -> tx.getTransaction().getMonetaryAmount());

        return new ViewerFilter()
        {
            @Override
            public Object[] filter(Viewer viewer, Object parent, Object[] elements)
            {
                return filterText == null ? elements : super.filter(viewer, parent, elements);
            }

            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element)
            {
                TransactionPair<?> tx = transaction.apply(element);

                for (Function<TransactionPair<?>, Object> label : searchLabels)
                {
                    Object l = label.apply(tx);
                    if (l == null)
                        continue;

                    // If this is a numeric field, do a numeric comparison
                    // to handle formatting differences (commas, periods, etc.)
                    if (l instanceof Money money) // NOSONAR
                    {
                        if (TextUtil.isNumericSearchMatch(filterText, money.getAmount() / Values.Money.divider()))
                            return true;
                    }
                    else if (l instanceof Long number)
                    {
                        if (TextUtil.isNumericSearchMatch(filterText, number.doubleValue() / Values.Share.divider()))
                            return true;
                    }
                    else if (l instanceof Number number)
                    {
                        if (TextUtil.isNumericSearchMatch(filterText, number.doubleValue()))
                            return true;
                    }
                    else
                    {
                        if (l.toString().toLowerCase().indexOf(filterText) >= 0)
                            return true;
                    }
                }

                return false;
            }
        };
    }

    private String logPrefix(Text search)
    {
        return "TransactionSearchField@" + Integer.toHexString(System.identityHashCode(this)) + "/Text@"
                        + Integer.toHexString(System.identityHashCode(search));
    }

    private void logSwtEvent(String label, Text search, Event event)
    {
        if (search.isDisposed())
        {
            PortfolioPlugin.info(String.format("%s %s (widget disposed) type=%s detail=%s", logPrefix(search), label,
                            describeEventType(event.type), describeDetail(event.detail)));
            return;
        }

        Point selection = search.getSelection();

        String message = String.format(
                        "%s %s type=%s detail=%s doit=%s text=\"%s\" trimmed=\"%s\" selectionStart=%d selectionEnd=%d"
                                        + " caret=%d keyCode=%d character=%s stateMask=0x%X button=%d time=%d widgetClass=%s",
                        logPrefix(search), label, describeEventType(event.type), describeDetail(event.detail),
                        Boolean.toString(event.doit), search.getText(), search.getText().trim(), selection.x,
                        selection.y, search.getCaretPosition(), event.keyCode, describeCharacter(event.character),
                        event.stateMask, event.button, event.time,
                        event.widget != null ? event.widget.getClass().getSimpleName() : "null");
        PortfolioPlugin.info(message);
    }

    private void logFilterUpdate(Text search, String previousFilterText, String trimmedText, String reason)
    {
        if (search.isDisposed())
        {
            PortfolioPlugin.info(String.format(
                            "%s modifyListener: skipped logging filter update because widget is disposed", logPrefix(search)));
            return;
        }

        String message = String.format(
                        "%s modifyListener: raw=\"%s\" trimmed=\"%s\" previousFilterText=%s newFilterText=%s reason=%s",
                        logPrefix(search), search.getText(), trimmedText, safe(previousFilterText), safe(filterText),
                        reason);
        PortfolioPlugin.info(message);
    }

    private String describeEventType(int type)
    {
        return switch (type)
        {
            case SWT.Selection -> "SWT.Selection";
            case SWT.DefaultSelection -> "SWT.DefaultSelection";
            case SWT.MouseDown -> "SWT.MouseDown";
            case SWT.MouseUp -> "SWT.MouseUp";
            case SWT.MouseDoubleClick -> "SWT.MouseDoubleClick";
            case SWT.FocusIn -> "SWT.FocusIn";
            case SWT.FocusOut -> "SWT.FocusOut";
            case SWT.KeyDown -> "SWT.KeyDown";
            case SWT.KeyUp -> "SWT.KeyUp";
            case SWT.Traverse -> "SWT.Traverse";
            case SWT.Verify -> "SWT.Verify";
            case SWT.Modify -> "SWT.Modify";
            default -> "type(" + type + ")";
        };
    }

    private String describeDetail(int detail)
    {
        return switch (detail)
        {
            case SWT.NONE -> "SWT.NONE";
            case SWT.CANCEL -> "SWT.CANCEL";
            case SWT.TRAVERSE_RETURN -> "SWT.TRAVERSE_RETURN";
            case SWT.TRAVERSE_TAB_NEXT -> "SWT.TRAVERSE_TAB_NEXT";
            case SWT.TRAVERSE_TAB_PREVIOUS -> "SWT.TRAVERSE_TAB_PREVIOUS";
            case SWT.TRAVERSE_ESCAPE -> "SWT.TRAVERSE_ESCAPE";
            case SWT.TRAVERSE_PAGE_NEXT -> "SWT.TRAVERSE_PAGE_NEXT";
            case SWT.TRAVERSE_PAGE_PREVIOUS -> "SWT.TRAVERSE_PAGE_PREVIOUS";
            default -> "detail(" + detail + ")";
        };
    }

    private String describeCharacter(char character)
    {
        if (character == 0)
            return "<null>";
        if (Character.isISOControl(character))
            return String.format("0x%04X(control)", (int) character);
        return String.format("'%s'(0x%04X)", character, (int) character);
    }

    private String safe(Object value)
    {
        return value == null ? "<null>" : value.toString();
    }
}
