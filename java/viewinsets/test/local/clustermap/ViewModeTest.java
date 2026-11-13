package local.clustermap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class ViewModeTest {
    public static final class FakeService {
        private final FakeModel model = new FakeModel();
        public Object getModel(int id) {
            if (id != 402521) throw new IllegalArgumentException("model " + id);
            return model;
        }
    }

    public static final class FakeModel {
        int value;
        public int getValue() {
            return value;
        }
    }

    public static class FakeControllerParent {
        private static final FakeService hmiService = new FakeService();
    }

    public static final class FakeController extends FakeControllerParent {
    }

    public static final class FakeEvent {
        private final int modelId;
        FakeEvent(int modelId) {
            this.modelId = modelId;
        }
        public int getModelId() {
            return modelId;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("state path required");
        ViewMode.statePath = args[0];
        FakeController controller = new FakeController();

        FakeControllerParent.hmiService.model.value = 1;
        ViewMode.sync(controller, null);
        expect("classic");

        FakeControllerParent.hmiService.model.value = 0;
        ViewMode.sync(controller, new FakeEvent(123));
        expect("classic");
        ViewMode.sync(controller, new FakeEvent(402521));
        expect("large");

        FakeControllerParent.hmiService.model.value = 1;
        ViewMode.sync(controller, new FakeEvent(402521));
        expect("classic");
    }

    private static void expect(String mode) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(ViewMode.statePath));
        String actual;
        try {
            actual = reader.readLine();
        } finally {
            reader.close();
        }
        if (!mode.equals(actual)) {
            throw new AssertionError("expected " + mode + ", got " + actual);
        }
    }
}
