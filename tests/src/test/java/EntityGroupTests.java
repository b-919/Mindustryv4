import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.entities.impl.BaseEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the bulk removal path in {@link EntityGroup#updateEvents()}.
 * <p>
 * That path reads {@code Array.items} as an {@code Object[]}: libGDX allocates an
 * {@code Object[]} for generic arrays, so any accidental cast to the erased element array type
 * ({@code Entity[]}) fails at runtime with a ClassCastException. These tests exercise it with
 * enough removals to take the bulk branch.
 */
public class EntityGroupTests{

    static class TestEntity extends BaseEntity{
        int removed;

        @Override
        public void removed(){
            removed++;
        }
    }

    @Test
    void bulkRemovalCompactsByID(){
        EntityGroup<TestEntity> group = new EntityGroup<>(TestEntity.class, false);

        List<TestEntity> entities = new ArrayList<>();
        for(int i = 0; i < 200; i++){
            TestEntity entity = new TestEntity();
            entities.add(entity);
            group.add(entity);
        }
        group.updateEvents();

        assertEquals(200, group.size());

        //more than the bulk-removal threshold, all in the same frame
        for(int i = 0; i < 150; i++){
            entities.get(i).remove();
        }
        group.updateEvents();

        assertEquals(50, group.size());

        for(int i = 0; i < 150; i++){
            assertEquals(1, entities.get(i).removed, "removed() must run exactly once");
        }
        for(int i = 150; i < 200; i++){
            assertEquals(0, entities.get(i).removed);
            assertTrue(group.all().contains(entities.get(i), true), "surviving entity must be kept");
        }
    }

    @Test
    void smallRemovalStillWorks(){
        EntityGroup<TestEntity> group = new EntityGroup<>(TestEntity.class, false);

        TestEntity a = new TestEntity();
        TestEntity b = new TestEntity();
        group.add(a);
        group.add(b);
        group.updateEvents();

        a.remove();
        group.updateEvents();

        assertEquals(1, group.size());
        assertEquals(1, a.removed);
        assertTrue(group.all().contains(b, true));
    }

    @Test
    void additionsAndBulkRemovalsInSameFrame(){
        EntityGroup<TestEntity> group = new EntityGroup<>(TestEntity.class, false);

        List<TestEntity> existing = new ArrayList<>();
        for(int i = 0; i < 20; i++){
            TestEntity entity = new TestEntity();
            existing.add(entity);
            group.add(entity);
        }
        group.updateEvents();

        //half removed and a few added in the same frame, exercising the bulk branch and the add queue together
        for(int i = 0; i < 10; i++){
            existing.get(i).remove();
        }

        List<TestEntity> added = new ArrayList<>();
        for(int i = 0; i < 5; i++){
            TestEntity entity = new TestEntity();
            added.add(entity);
            group.add(entity);
        }

        group.updateEvents();

        assertEquals(15, group.size());

        for(int i = 0; i < 10; i++){
            assertEquals(1, existing.get(i).removed);
        }
        for(int i = 10; i < 20; i++){
            assertEquals(0, existing.get(i).removed);
            assertTrue(group.all().contains(existing.get(i), true));
        }
        for(TestEntity entity : added){
            assertTrue(group.all().contains(entity, true));
        }
    }
}
