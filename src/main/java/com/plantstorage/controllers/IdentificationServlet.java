package com.plantstorage.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.plantstorage.decisiontree.PlantDecisionTree;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

/** Stateless endpoint: each browser supplies its own answer path; no Drive writes. */
public final class IdentificationServlet extends HttpServlet {
    private final Gson gson = new Gson();
    private final PlantDecisionTree tree = new PlantDecisionTree();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            String path = req.getParameter("answers");
            if (path != null && path.length() > 4096) throw new IllegalArgumentException("Answer history too long");
            PlantDecisionTree.Answer[] answers = path == null ? new PlantDecisionTree.Answer[0]
                : gson.fromJson(path, PlantDecisionTree.Answer[].class);
            if (answers == null) throw new IllegalArgumentException("Answer history must be an array");
            resp.getWriter().write(gson.toJson(tree.evaluate(Arrays.asList(answers))));
        } catch (JsonParseException | IllegalArgumentException ex) {
            resp.setStatus(400);
            resp.getWriter().write(gson.toJson(Map.of("error", "Invalid answers. Please restart the identification.")));
        }
    }
}
