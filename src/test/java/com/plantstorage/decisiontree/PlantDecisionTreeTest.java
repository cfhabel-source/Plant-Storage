package com.plantstorage.decisiontree;

import java.util.*;
import static com.plantstorage.decisiontree.PlantDecisionTree.*;

/** Standalone tests: run with Java 21, no JUnit dependency required. */
public final class PlantDecisionTreeTest {
    private static final PlantDecisionTree tree = new PlantDecisionTree();
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static Result walk(Map<String, String> observations) {
        List<Answer> path = new ArrayList<>();
        Result result = tree.evaluate(path);
        while (!result.finished()) {
            check(path.size() < 12, "Path must terminate");
            String question = result.question().id();
            path.add(new Answer(question, observations.getOrDefault(question, "unknown")));
            result = tree.evaluate(path);
        }
        return result;
    }
    public static void main(String[] args) {
        check(PLANTS.size() == 32, "Exactly 32 entries");
        check(PLANTS.stream().map(Plant::id).distinct().count() == 32, "Unique IDs");
        check(tree.evaluate(List.of()).candidates().size() == 32, "Initial candidates");
        check(walk(Map.of()).candidates().size() == 32, "Unknown must preserve every candidate");
        check(walk(Map.of("foliage", "no")).candidates().size() == 32, "Dormant plant retains all entries");
        for (Plant plant : PLANTS) {
            Map<String, String> observations = new HashMap<>();
            observations.put("foliage", "yes");
            observations.put("flowers", "no");
            plant.traits().forEach((key, values) -> observations.put(key, values.stream().sorted().findFirst().orElseThrow()));
            Result result = walk(observations);
            check(result.candidates().contains(plant), "Reachable without flowers: " + plant.name());
        }
        Result fern = walk(Map.of("foliage","yes","habit","fern","fernDivision","twice"));
        check(fern.candidates().size() == 1 && fern.candidates().get(0).id().equals("dryopteris"), "Male fern branch");
        Result ferns = walk(Map.of("foliage","yes","habit","fern"));
        check(ferns.candidates().size() == 2, "Unknown fern division preserves both");
        Result contradiction = walk(Map.of("foliage","yes","habit","herb","leaf","frond"));
        check(contradiction.candidates().isEmpty() && contradiction.finished(), "Contradictory observations produce no match");
        Result catmints = walk(Map.of("foliage","yes","habit","herb","leaf","simple","arrangement","opposite","edge","toothed","surface","hairy","flowers","no"));
        check(catmints.candidates().stream().anyMatch(p -> p.id().equals("n-faassenii")) &&
              catmints.candidates().stream().anyMatch(p -> p.id().equals("n-racemosa")), "Catmints remain ambiguous");
        Result noFlowers = walk(Map.of("foliage","yes","flowers","no"));
        check(noFlowers.answers().stream().noneMatch(a -> a.questionId().equals("flowerShape")), "No flower questions without flowers");
        Result withFlowers = walk(Map.of("foliage","yes","habit","herb","leaf","lobed","flowers","yes","flowerShape","pincushion"));
        check(withFlowers.candidates().size() == 1 && withFlowers.candidates().get(0).id().equals("astrantia"), "Optional flowers refine candidates");
        List<Answer> path = List.of(new Answer("foliage","yes"), new Answer("habit","climber"));
        check(tree.evaluate(path.subList(0,1)).candidates().size() == 32, "Back restores candidates");
        check(tree.evaluate(List.of()).candidates().size() == 32, "Restart independent of other requests");
        try { tree.evaluate(List.of(new Answer("habit","fern"))); throw new AssertionError("Invalid path accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { tree.evaluate(Arrays.asList((Answer)null)); throw new AssertionError("Null answer accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("Passed " + checks + " checks, including all 32 non-flowering paths.");
    }
}
