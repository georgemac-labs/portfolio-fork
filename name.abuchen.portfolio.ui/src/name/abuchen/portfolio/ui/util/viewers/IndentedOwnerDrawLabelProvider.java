package name.abuchen.portfolio.ui.util.viewers;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.OwnerDrawLabelProvider;
import org.eclipse.jface.viewers.ViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.TableItem;

public class IndentedOwnerDrawLabelProvider extends OwnerDrawLabelProvider
{
    private static final int INDENT_PIXELS = 20;
    private static final int IMAGE_TEXT_GAP = 4;
    private static final String ARROW = "\u2937 ";

    private final ColumnLabelProvider delegate;
    private final ToIntFunction<Object> depthProvider;
    private final Predicate<Object> showArrow;

    public IndentedOwnerDrawLabelProvider(ColumnLabelProvider delegate, ToIntFunction<Object> depthProvider,
                    Predicate<Object> showArrow)
    {
        this.delegate = delegate;
        this.depthProvider = depthProvider;
        this.showArrow = showArrow;
    }

    @Override
    protected void initialize(ColumnViewer viewer, ViewerColumn column)
    {
        super.initialize(viewer, column);
    }

    @Override
    public String getToolTipText(Object element)
    {
        return delegate.getToolTipText(element);
    }

    @Override
    public Point getToolTipShift(Object object)
    {
        return delegate.getToolTipShift(object);
    }

    @Override
    public int getToolTipDisplayDelayTime(Object object)
    {
        return delegate.getToolTipDisplayDelayTime(object);
    }

    @Override
    public int getToolTipTimeDisplayed(Object object)
    {
        return delegate.getToolTipTimeDisplayed(object);
    }

    @Override
    public boolean useNativeToolTip(Object object)
    {
        return delegate.useNativeToolTip(object);
    }

    @Override
    public Color getToolTipBackgroundColor(Object object)
    {
        return delegate.getToolTipBackgroundColor(object);
    }

    @Override
    public Color getToolTipForegroundColor(Object object)
    {
        return delegate.getToolTipForegroundColor(object);
    }

    @Override
    public Font getToolTipFont(Object object)
    {
        return delegate.getToolTipFont(object);
    }

    @Override
    protected void measure(Event event, Object element)
    {
        String text = delegate.getText(element);
        if (text == null)
            text = "";
        Point size = event.gc.textExtent(text);

        int depth = depthProvider.applyAsInt(element);
        int indent = depth * INDENT_PIXELS;
        boolean hasArrow = depth > 0 && showArrow.test(element);
        int arrowWidth = hasArrow ? event.gc.textExtent(ARROW).x : 0;

        Image image = delegate.getImage(element);
        int imageWidth = image != null ? image.getBounds().width + IMAGE_TEXT_GAP : 0;

        event.width = indent + arrowWidth + imageWidth + size.x;
        event.height = Math.max(event.height, size.y);
    }

    @Override
    protected void paint(Event event, Object element)
    {
        Rectangle cellBounds = ((TableItem) event.item).getBounds(event.index);
        boolean isSelected = (event.detail & SWT.SELECTED) != 0 || (event.detail & SWT.HOT) != 0;

        if (!isSelected)
            fillBackground(event, element, cellBounds);

        int indent = depthProvider.applyAsInt(element) * INDENT_PIXELS;

        Font font = delegate.getFont(element);
        Font previousFont = null;
        if (font != null)
        {
            previousFont = event.gc.getFont();
            event.gc.setFont(font);
        }

        Color oldForeground = null;
        Color foreground = isSelected ? null : delegate.getForeground(element);
        if (foreground != null)
        {
            oldForeground = event.gc.getForeground();
            event.gc.setForeground(foreground);
        }

        int depth = depthProvider.applyAsInt(element);
        int x = event.x + depth * INDENT_PIXELS;

        if (depth > 0 && showArrow.test(element))
        {
            Point arrowExtent = event.gc.textExtent(ARROW);
            int arrowY = event.y + Math.max(0, (cellBounds.height - arrowExtent.y) / 2);
            event.gc.drawText(ARROW, x, arrowY, true);
            x += arrowExtent.x;
        }

        Image image = delegate.getImage(element);
        if (image != null)
        {
            Rectangle imgBounds = image.getBounds();
            int imgY = event.y + Math.max(0, (cellBounds.height - imgBounds.height) / 2);
            event.gc.drawImage(image, x, imgY);
            x += imgBounds.width + IMAGE_TEXT_GAP;
        }

        String text = delegate.getText(element);
        if (text != null && !text.isEmpty())
        {
            Point textExtent = event.gc.textExtent(text);
            int textY = event.y + Math.max(0, (cellBounds.height - textExtent.y) / 2);
            event.gc.drawText(text, x, textY, true);
        }

        if (oldForeground != null)
            event.gc.setForeground(oldForeground);

        if (previousFont != null)
            event.gc.setFont(previousFont);
    }

    private void fillBackground(Event event, Object element, Rectangle bounds)
    {
        Color background = delegate.getBackground(element);
        if (background != null)
        {
            Color old = event.gc.getBackground();
            event.gc.setBackground(background);
            event.gc.fillRectangle(bounds.x, bounds.y, bounds.width, bounds.height);
            event.gc.setBackground(old);
        }
    }

    @Override
    protected void erase(Event event, Object element)
    {
        // use OS-specific background for selection
    }

    @Override
    public void dispose()
    {
        delegate.dispose();
        super.dispose();
    }
}
