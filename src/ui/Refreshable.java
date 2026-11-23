package ui;

/**
 * An interface is a CONTRACT: a list of methods that a class promises to have.
 * Anything that is Refreshable can be told "reload your data" (refresh) and
 * "a second has passed" (tick). MainFrame uses this contract without knowing which screen it is.
 */
public interface Refreshable {
    /** Reload the data from the database and redraw. */
    void refresh();

    /** Called every second by the Swing Timer (for live clocks). */
    void tick();
}
