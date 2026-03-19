package name.abuchen.portfolio.ui.util;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.util.action.MenuContribution;

public class TaxonomySelector
{
    private static final String PREF_NONE = "@none";

    private final Client client;
    private final IPreferenceStore prefs;
    private final String prefKey;
    private final Runnable onChange;

    private Taxonomy taxonomy;

    public TaxonomySelector(Client client, IPreferenceStore prefs, String prefKey, Runnable onChange)
    {
        this.client = client;
        this.prefs = prefs;
        this.prefKey = prefKey;
        this.onChange = onChange;

        loadTaxonomy();
    }

    private void loadTaxonomy()
    {
        String taxonomyId = prefs.getString(prefKey);

        if (PREF_NONE.equals(taxonomyId))
            return;

        if (taxonomyId != null && !taxonomyId.isBlank())
        {
            for (Taxonomy t : client.getTaxonomies())
            {
                if (taxonomyId.equals(t.getId()))
                {
                    this.taxonomy = t;
                    break;
                }
            }
        }

        if (this.taxonomy == null && !client.getTaxonomies().isEmpty())
            this.taxonomy = client.getTaxonomies().get(0);
    }

    public Taxonomy getTaxonomy()
    {
        return taxonomy;
    }

    public void saveTaxonomy()
    {
        if (taxonomy != null)
            prefs.setValue(prefKey, taxonomy.getId());
        else
            prefs.setValue(prefKey, PREF_NONE);
    }

    public void contributeToMenu(IMenuManager manager)
    {
        manager.add(new LabelOnly(Messages.LabelTaxonomies));

        var noneAction = new SimpleAction(Messages.LabelUseNoTaxonomy, a -> {
            taxonomy = null;
            saveTaxonomy();
            onChange.run();
        });
        noneAction.setChecked(taxonomy == null);
        manager.add(noneAction);

        for (final Taxonomy t : client.getTaxonomies())
        {
            manager.add(new MenuContribution(t.getName(), () -> {
                taxonomy = t;
                saveTaxonomy();
                onChange.run();
            }, t.equals(taxonomy)));
        }

        manager.add(new Separator());
    }
}
