package redacted.client.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Describes one positional argument a command accepts. Every command declares its
 * arguments in order and the shared plumbing derives three things from them:
 * the colored usage string (rendered by {@link Command#getUsage()}), the default
 * tab-completion values, and the per-argument help block shown by
 * {@code .help <command>}.
 *
 * <p>A literal argument exposes a fixed set of choices (each can carry its own
 * one-line description). A text/greedy argument is free-form; give it a
 * {@link Supplier} of completion values and the names of live configs, modules,
 * themes, etc. appear while tabbing.
 */
public final class Argument {

    public static final class Option {
        private final String value;
        private final String description;

        public Option(final String value, final String description) {
            this.value = value;
            this.description = description;
        }

        public String value() {
            return value;
        }

        public String description() {
            return description;
        }
    }

    private final String name;
    private final String description;
    private final boolean required;
    private final boolean greedy;
    private final List<Option> options;
    private final Supplier<List<String>> values;

    private Argument(final String name, final String description, final boolean required,
                     final boolean greedy, final List<Option> options,
                     final Supplier<List<String>> values) {
        this.name = name;
        this.description = description;
        this.required = required;
        this.greedy = greedy;
        this.options = options;
        this.values = values;
    }

    public static Argument literal(final String name, final String description, final Option... options) {
        return new Argument(name, description, true, false,
                options == null ? Collections.<Option>emptyList() : Arrays.asList(options), null);
    }

    public static Argument literal(final String name, final String description, final String... values) {
        final List<Option> opts = new ArrayList<Option>();
        for (final String value : values) {
            opts.add(new Option(value, null));
        }
        return new Argument(name, description, true, false, opts, null);
    }

    public static Argument optionalLiteral(final String name, final String description, final Option... options) {
        return new Argument(name, description, false, false,
                options == null ? Collections.<Option>emptyList() : Arrays.asList(options), null);
    }

    public static Argument optionalLiteral(final String name, final String description, final String... values) {
        final List<Option> opts = new ArrayList<Option>();
        for (final String value : values) {
            opts.add(new Option(value, null));
        }
        return new Argument(name, description, false, false, opts, null);
    }

    public static Argument text(final String name, final String description) {
        return new Argument(name, description, true, false, null, null);
    }

    public static Argument text(final String name, final String description, final Supplier<List<String>> values) {
        return new Argument(name, description, true, false, null, values);
    }

    public static Argument optionalText(final String name, final String description, final Supplier<List<String>> values) {
        return new Argument(name, description, false, false, null, values);
    }

    public static Argument greedy(final String name, final String description, final Supplier<List<String>> values) {
        return new Argument(name, description, true, true, null, values);
    }

    public static Argument optionalGreedy(final String name, final String description, final Supplier<List<String>> values) {
        return new Argument(name, description, false, true, null, values);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isGreedy() {
        return greedy;
    }

    public boolean hasChoices() {
        return options != null && !options.isEmpty();
    }

    public List<Option> getOptions() {
        return options == null ? Collections.<Option>emptyList() : options;
    }

    public List<String> getValues() {
        if (options != null && !options.isEmpty()) {
            final List<String> out = new ArrayList<String>();
            for (final Option option : options) {
                out.add(option.value());
            }
            return out;
        }
        if (values != null) {
            final List<String> supplied = values.get();
            return supplied == null ? Collections.<String>emptyList() : supplied;
        }
        return Collections.emptyList();
    }

    public String optionDescription(final String value) {
        for (final Option option : getOptions()) {
            if (option.value().equalsIgnoreCase(value)) {
                return option.description();
            }
        }
        return null;
    }
}
