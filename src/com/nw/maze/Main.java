package com.nw.maze;

import java.util.Comparator;
import java.util.PriorityQueue;

// import org.springframework.CollectionUtils;

public class Main {

    private static final int directions[][] = { { -1, 0 }, { 0, 1 }, { 1, 0 }, { 0, -1 } };
    // private static final String FILE_NAME = "src/com/nw/maze/maze_101_101.txt";
    private static final String FILE_NAME = "m15_15.txt";
    // BLOCK_SIZE unused after fixing frame to 1920x1080
    // private static final int BLOCK_SIZE = 10;
    MazeFrame frame;
    MazeData data;
    private volatile boolean cancelled = false;
    private Thread currentRunner;

    public void initFrame() {
        data = new MazeData(FILE_NAME);
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        frame = new MazeFrame("Maze Solver - " + getMazeLabel(), screen.width-50, screen.height-110);
        frame.setMazeFileName(FILE_NAME);
        frame.setControlListener(new MazeFrame.ControlListener() {
            @Override
            public void onRunRequested(String algorithmName) {
                frame.setControlsEnabled(false);
                cancelled = false;
                currentRunner = new Thread(() -> {
                    try {
                        runWithAlgorithm(algorithmName);
                    } finally {
                        // Re-enable controls on EDT after run completes
                        javax.swing.SwingUtilities.invokeLater(() -> frame.setControlsEnabled(true));
                    }
                }, "maze-runner");
                currentRunner.start();
            }
            @Override
            public void onResetRequested() {
                // signal cancellation and interrupt runner if present
                cancelled = true;
                if (currentRunner != null && currentRunner.isAlive()) {
                    currentRunner.interrupt();
                }
                resetState();
                // Reset GA parameter controls to defaults
                frame.resetGaParametersToDefaults();
            }

            @Override
            public void onImportRequested(String filePath) {
                try {
                    MazeData newData = new MazeData(filePath);
                    data = newData;
                    int bs = frame.getBlockSize();
                    frame.resizeToBlock(bs);
                    frame.setTitle("Maze Solver - " + getMazeLabel());
                    frame.setMazeFileName(filePath);
                    resetState();
                    frame.render(data);
                } catch (RuntimeException ex) {
                    javax.swing.SwingUtilities.invokeLater(() ->
                        javax.swing.JOptionPane.showMessageDialog(frame, ex.getMessage(), "Load Error", javax.swing.JOptionPane.ERROR_MESSAGE)
                    );
                }
            }
        });
        frame.render(data);
        // Wait for user to press Run; no auto-execution
    }

    private void resetState() {
        for (int i = 0; i < data.N(); i++) {
            for (int j = 0; j < data.M(); j++) {
                data.visited[i][j] = false;
                data.path[i][j] = false;
                data.result[i][j] = false;
            }
        }
        frame.setTitle("Maze Solver - " + getMazeLabel());
        frame.render(data);
    }

    // Helper: returns a friendly label for the currently loaded maze (file name or size)
    private String getMazeLabel() {
        // Prefer original source file name via toString if MazeData exposes it
        try {
            if (data != null) {
                String s = data.toString();
                if (s != null && !s.trim().isEmpty()) return s;
            }
        } catch (Throwable ignored) {}
        // Fallback to dimensions
        if (data != null) {
            return data.N() + "x" + data.M();
        }
        return "(no maze)";
    }

    private void runWithAlgorithm(String algo) {
        // Reset state arrays
        for (int i = 0; i < data.N(); i++) {
            for (int j = 0; j < data.M(); j++) {
                data.visited[i][j] = false;
                data.path[i][j] = false;
                data.result[i][j] = false;
            }
        }

        switch (algo) {
            case "BFS":
                runBFS();
                return;
            case "A*":
                runAStar();
                return;
            case "Genetic":
                runGeneticStub();
                return;
            case "Dijkstra":
            default:
                runDijkstra();
        }
    }

    private void runDijkstra() {
        // Dijkstra's algorithm on grid with per-cell weights
        int rows = data.N();
        int cols = data.M();
        int[][] dist = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) dist[i][j] = Integer.MAX_VALUE;
        }

        Node start = new Node(data.getEntranceX(), data.getEntranceY(), 0, null);
        if (data.inArea(start.x, start.y)) {
            dist[start.x][start.y] = 0;
        }

        PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));
        pq.add(start);

        boolean isSolved = false;
        int visitedCount = 0;
        long t0 = System.nanoTime();
        Node endNode = null;

        while (!pq.isEmpty() && !cancelled) {
            Node cur = pq.poll();
            if (data.visited[cur.x][cur.y]) continue; // finalized already
            data.visited[cur.x][cur.y] = true;
            visitedCount++;

            setData(cur.x, cur.y, true); // visualize exploration

            if (cur.x == data.getExitX() && cur.y == data.getExitY()) {
                isSolved = true;
                endNode = cur;
                break;
            }

            for (int[] d : directions) {
                int nx = cur.x + d[0];
                int ny = cur.y + d[1];
                if (!data.inArea(nx, ny)) continue;
                if (data.getMazeChar(nx, ny) != MazeData.ROAD) continue; // skip walls
                if (data.visited[nx][ny]) continue;
                int stepCost = 1;
                if (data.weight != null) {
                    int w = data.weight[nx][ny];
                    stepCost = (w > 0 ? w : 1);
                }
                int newCost = (cur.cost == Integer.MAX_VALUE ? Integer.MAX_VALUE : cur.cost + stepCost);
                if (newCost < dist[nx][ny]) {
                    dist[nx][ny] = newCost;
                    pq.add(new Node(nx, ny, newCost, cur));
                }
            }
        }

        long t1 = System.nanoTime();

        if (isSolved && endNode != null) {
            int steps = findPath(endNode); // mark result path and count steps
            int totalCost = endNode.cost;
            long ms = (t1 - t0) / 1_000_000L;
            frame.updateMetrics(totalCost, steps, visitedCount, ms, "Dijkstra");
        } else {
            frame.updateMetrics(null, null, visitedCount, (System.nanoTime()-t0)/1_000_000L, "Dijkstra");
            System.out.println("The maze has NO solution!");
        }
        setData(-1, -1, false);
    }

    private void runBFS() {
        java.util.ArrayDeque<Position> queue = new java.util.ArrayDeque<>();
        Position entrance = new Position(data.getEntranceX(), data.getEntranceY(), null);
        queue.add(entrance);
        if (data.inArea(entrance.x, entrance.y)) data.visited[entrance.x][entrance.y] = true;

        boolean isSolved = false;
        int visitedCount = 0;
        long t0 = System.nanoTime();
        Position end = null;

        while (!queue.isEmpty() && !cancelled) {
            Position cur = queue.poll();
            visitedCount++;
            setData(cur.x, cur.y, true);
            if (cur.x == data.getExitX() && cur.y == data.getExitY()) { isSolved = true; end = cur; break; }
            for (int[] d : directions) {
                int nx = cur.x + d[0], ny = cur.y + d[1];
                if (data.inArea(nx, ny) && !data.visited[nx][ny] && data.getMazeChar(nx,ny)==MazeData.ROAD) {
                    data.visited[nx][ny] = true;
                    queue.add(new Position(nx, ny, cur));
                }
            }
        }

        long t1 = System.nanoTime();
        if (isSolved && end != null) {
            int steps = findPath(end);
            long ms = (t1 - t0) / 1_000_000L;
            frame.updateMetrics(null, steps, visitedCount, ms, "BFS");
        } else {
            frame.updateMetrics(null, null, visitedCount, (System.nanoTime()-t0)/1_000_000L, "BFS");
        }
        setData(-1, -1, false);
    }

    private int findPath(Position p) {
        int steps = 0;
        Position cur = p;
        while (cur != null) {
            data.result[cur.x][cur.y] = true;
            cur = cur.prev;
            steps++;
        }
        return steps;
    }


    private void runAStar() {
        int rows = data.N(), cols = data.M();
        int[][] dist = new int[rows][cols];
        for (int i=0;i<rows;i++) for(int j=0;j<cols;j++) dist[i][j]=Integer.MAX_VALUE;
        Node start = new Node(data.getEntranceX(), data.getEntranceY(), 0, null);
        Node goal = new Node(data.getExitX(), data.getExitY(), 0, null);
        dist[start.x][start.y] = 0;

        Comparator<Node> cmp = (a,b) -> Integer.compare(a.cost + heuristic(a, goal), b.cost + heuristic(b, goal));
        PriorityQueue<Node> open = new PriorityQueue<>(cmp);
        open.add(start);

        boolean isSolved=false; int visitedCount=0; long t0=System.nanoTime(); Node end=null;
        while(!open.isEmpty() && !cancelled){
            Node cur = open.poll();
            if (data.visited[cur.x][cur.y]) continue;
            data.visited[cur.x][cur.y] = true; visitedCount++;
            setData(cur.x, cur.y, true);
            if (cur.x==goal.x && cur.y==goal.y){ isSolved=true; end=cur; break; }
            for(int[]d:directions){
                int nx=cur.x+d[0], ny=cur.y+d[1];
                if(!data.inArea(nx,ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD || data.visited[nx][ny]) continue;
                int stepCost = data.weight!=null && data.weight[nx][ny]>0 ? data.weight[nx][ny] : 1;
                int newCost = cur.cost + stepCost;
                if(newCost < dist[nx][ny]){ dist[nx][ny]=newCost; open.add(new Node(nx,ny,newCost,cur)); }
            }
        }
        long t1=System.nanoTime();
        if(isSolved && end!=null){ int steps=findPath(end); long ms=(t1-t0)/1_000_000L; frame.updateMetrics(end.cost, steps, visitedCount, ms, "A*"); }
        else { frame.updateMetrics(null, null, visitedCount, (System.nanoTime()-t0)/1_000_000L, "A*"); }
        setData(-1,-1,false);
    }

    private int heuristic(Node a, Node goal){
        // Manhattan distance as heuristic (weights ignored, admissible if weights>=1)
        return Math.abs(a.x - goal.x) + Math.abs(a.y - goal.y);
    }

    private void runGeneticStub() {
        // Placeholder for future GA: just show a message
        runGenetic();
    }

    private void runGenetic() {
        // Genetic algorithm with goal-directed bias and repair to reach goal
        final int populationSize = Math.max(10, frame.getGaPopulation());
        int estSteps = estimateShortestSteps();
        int area = data.N() * data.M();
        double scale = area >= 2500 ? 3.0 : 1.5; // bigger mazes get longer genomes
        int upperCap = Math.max(300, area / 2);  // allow larger cap for big mazes
        final int genomeLength = Math.max(
            Math.min((int)Math.round(estSteps * scale), upperCap),
            data.N() + data.M()
        );
        final double mutationRate = frame.getGaMutationRate();
        final double goalBias = frame.getGaGoalBias();
        java.util.Random rnd = new java.util.Random(42);

        // Helper to evaluate a genome
        class EvalResult { int cost; java.util.List<int[]> path; boolean reached; }
        java.util.function.Function<int[], EvalResult> evaluate = genome -> {
            // reset temp visited
            boolean[][] seen = new boolean[data.N()][data.M()];
            int x = data.getEntranceX(), y = data.getEntranceY();
            int cost = 0;
            java.util.ArrayList<int[]> path = new java.util.ArrayList<>();
            path.add(new int[]{x,y});
            seen[x][y] = true;
            for (int i=0;i<genome.length;i++) {
                int move = genome[i]%4;
                // Occasionally override with a goal-directed move
                if (rnd.nextDouble() < goalBias) {
                    move = chooseDirectedMove(x, y, data.getExitX(), data.getExitY());
                }
                int[] d = directions[move];
                int nx = x + d[0], ny = y + d[1];
                if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) {
                    cost += 50; // heavier penalty for invalid move
                    continue;
                }
                x = nx; y = ny;
                int w = data.weight!=null?data.weight[x][y]:1; int stepCost = w>0?w:1; cost += stepCost;
                if (!seen[x][y]) { seen[x][y]=true; }
                path.add(new int[]{x,y});
                if (x==data.getExitX() && y==data.getExitY()) break;
            }
            boolean reached = (x==data.getExitX() && y==data.getExitY());
            if (!reached) {
                // Penalize non-finished routes proportional to remaining Manhattan distance
                int md = Math.abs(x - data.getExitX()) + Math.abs(y - data.getExitY());
                cost += md * 120; // stronger steering toward goal
            }
            EvalResult r = new EvalResult(); r.cost=cost; r.path=path; r.reached=reached; return r;
        };

        // Initialize population (mix random and goal-directed seeded genomes)
        java.util.List<int[]> pop = new java.util.ArrayList<>(populationSize);
        int seeded = Math.max(2, populationSize / 10);
        for (int i=0;i<seeded;i++) {
            pop.add(generateDirectedGenome(genomeLength));
        }
        for (int i=seeded;i<populationSize;i++){
            int[] g=new int[genomeLength];
            for(int j=0;j<genomeLength;j++) g[j]=rnd.nextInt(4);
            pop.add(g);
        }

        int bestCost = Integer.MAX_VALUE; java.util.List<int[]> bestPath=null; boolean bestReached=false; String algoName="Genetic";
        long t0 = System.nanoTime();
        int gen = 0;
        // Keep evolving without a generation cap; rely on Cancel to stop
        while (!cancelled && !bestReached) {
            // Evaluate
            java.util.List<EvalResult> results = new java.util.ArrayList<>(populationSize);
            for (int[] g : pop) results.add(evaluate.apply(g));
            // Sort by reached then cost
            results.sort((r1, r2) -> {
                int c1 = (r1.reached ? 0 : 1);
                int c2 = (r2.reached ? 0 : 1);
                if (c1 != c2) return Integer.compare(c1, c2);
                return Integer.compare(r1.cost, r2.cost);
            });
            // Elitism
            java.util.List<int[]> next = new java.util.ArrayList<>(populationSize);
            int eliteCount = Math.max(1, Math.min(frame.getGaElitismCount(), populationSize-1));
            for (int i=0;i<eliteCount;i++) {
                int[] elite = pop.get(i);
                // Small greedy repair to help elites approach the goal if not reached
                if (!results.get(i).reached) {
                    greedyRepair(elite, genomeLength);
                }
                next.add(elite);
            }
            // Track best
            EvalResult br = results.get(0);
            if (br.cost < bestCost || br.reached) { bestCost=br.cost; bestPath=br.path; bestReached = br.reached; }
            // Animate the GA travelling: render the current best candidate's path
            if (!cancelled && br.path != null) {
                // Clear transient exploration marks and animate as travelling path
                clearTransientMarks();
                renderTravellingPath(br.path);
            }
            if (bestReached) break; // stop once a path reaches the goal
            // Crossover + mutation to refill
            while (next.size() < populationSize) {
                int[] p1 = pop.get(rnd.nextInt(Math.max(eliteCount, 4)));
                int[] p2 = pop.get(rnd.nextInt(Math.max(eliteCount, 4)));
                int[] child = new int[genomeLength];
                int cut = 1 + rnd.nextInt(genomeLength-1);
                System.arraycopy(p1, 0, child, 0, cut);
                System.arraycopy(p2, cut, child, cut, genomeLength-cut);
                // mutation
                for (int j=0;j<genomeLength;j++) {
                    if (rnd.nextDouble() < mutationRate) child[j] = rnd.nextInt(4);
                }
                // Occasionally bias a segment toward goal
                if (rnd.nextDouble() < 0.15) {
                    directedSegmentMutation(child);
                }
                // If child still looks poor (heuristic), apply greedy repair
                if (rnd.nextDouble() < 0.2) {
                    greedyRepair(child, genomeLength);
                }
                next.add(child);
            }
            pop = next;
            // Occasionally update UI with cost-only to avoid clutter
            if (gen % 10 == 0) {
                frame.updateMetrics(bestCost, null, null, (System.nanoTime()-t0)/1_000_000L, algoName);
            }
            gen++;
        }
        long t1 = System.nanoTime();
        // Render best path (paint finished route only once)
        resetState();
        if (bestPath != null) {
            for (int[] cell : bestPath) {
                if (cancelled) break;
                int bx = cell[0], by = cell[1];
                if (data.inArea(bx, by)) data.result[bx][by] = true;
            }
            frame.render(data);
        }
        // Final report: show only cost of best way
        frame.updateMetrics(bestCost, null, null, (t1-t0)/1_000_000L, algoName);
        setData(-1, -1, false);
    }

    // Helper: clear transient exploration marks used for travelling animation
    private void clearTransientMarks() {
        for (int i = 0; i < data.N(); i++) {
            for (int j = 0; j < data.M(); j++) {
                data.path[i][j] = false;
            }
        }
        frame.render(data);
    }

    // Animate the current best GA candidate path as travelling steps
    private void renderTravellingPath(java.util.List<int[]> path) {
        for (int[] cell : path) {
            if (cancelled) break;
            int x = cell[0], y = cell[1];
            setData(x, y, true); // uses pause based on UI speed
        }
        // After travelling, keep the last travelled cells marked as path
        frame.render(data);
    }

    // Choose a move that reduces Manhattan distance and avoids walls when possible
    private int chooseDirectedMove(int x, int y, int gx, int gy) {
        int bestMove = -1;
        int bestDist = Math.abs(gx - x) + Math.abs(gy - y);
        for (int m = 0; m < 4; m++) {
            int nx = x + directions[m][0];
            int ny = y + directions[m][1];
            if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) continue;
            int dist = Math.abs(gx - nx) + Math.abs(gy - ny);
            if (dist < bestDist) { bestDist = dist; bestMove = m; }
        }
        if (bestMove != -1) return bestMove;
        // fallback: prefer any valid move
        java.util.ArrayList<Integer> candidates = new java.util.ArrayList<>();
        for (int m = 0; m < 4; m++) {
            int nx = x + directions[m][0];
            int ny = y + directions[m][1];
            if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) continue;
            candidates.add(m);
        }
        if (!candidates.isEmpty()) return candidates.get(new java.util.Random().nextInt(candidates.size()));
        return new java.util.Random().nextInt(4);
    }

    // Replace a random segment with goal-directed steps
    private void directedSegmentMutation(int[] g) {
        java.util.Random rnd = new java.util.Random();
        int segLen = Math.max(5, Math.min(20, g.length / 6));
        int startIdx = rnd.nextInt(Math.max(1, g.length - segLen));
        // Replace segment with goal-directed steps based on current simulated position
        int x = data.getEntranceX(), y = data.getEntranceY();
        for (int i = 0; i < startIdx; i++) {
            int mv = g[i] % 4;
            int nx = x + directions[mv][0];
            int ny = y + directions[mv][1];
            if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) continue;
            x = nx; y = ny;
        }
        for (int i = startIdx; i < Math.min(g.length, startIdx + segLen); i++) {
            int mv = chooseDirectedMove(x, y, data.getExitX(), data.getExitY());
            g[i] = mv;
            int nx = x + directions[mv][0];
            int ny = y + directions[mv][1];
            if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) break;
            x = nx; y = ny;
        }
    }

    // Append a small greedy tail to help elites approach goal
    private void greedyRepair(int[] g, int genomeLength) {
        int x = data.getEntranceX(), y = data.getEntranceY();
        for (int i = 0; i < genomeLength; i++) {
            int mv = g[i] % 4;
            int nx = x + directions[mv][0];
            int ny = y + directions[mv][1];
            if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) continue;
            x = nx; y = ny;
            if (x==data.getExitX() && y==data.getExitY()) return;
        }
        // Replace last few steps with goal-directed steps
        int tail = Math.min(20, genomeLength/4);
        for (int i = genomeLength - tail; i < genomeLength; i++) {
            int mv = chooseDirectedMove(x, y, data.getExitX(), data.getExitY());
            g[i] = mv;
            int nx = x + directions[mv][0];
            int ny = y + directions[mv][1];
            if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) break;
            x = nx; y = ny;
            if (x==data.getExitX() && y==data.getExitY()) break;
        }
    }

    // Estimate shortest steps from entrance to exit using unweighted BFS (ignores weights)
    private int estimateShortestSteps() {
        int n = data.N(), m = data.M();
        boolean[][] seen = new boolean[n][m];
        java.util.ArrayDeque<Position> q = new java.util.ArrayDeque<>();
        Position s = new Position(data.getEntranceX(), data.getEntranceY(), null);
        q.add(s);
        seen[s.x][s.y] = true;
        while (!q.isEmpty()) {
            Position cur = q.poll();
            if (cur.x == data.getExitX() && cur.y == data.getExitY()) {
                // count steps via backtracking
                int steps = 0;
                Position p = cur;
                while (p != null) { steps++; p = p.prev; }
                return steps;
            }
            for (int[] d : directions) {
                int nx = cur.x + d[0], ny = cur.y + d[1];
                if (data.inArea(nx, ny) && !seen[nx][ny] && data.getMazeChar(nx,ny)==MazeData.ROAD) {
                    seen[nx][ny] = true;
                    q.add(new Position(nx, ny, cur));
                }
            }
        }
        // fallback to Manhattan distance + padding if unreachable by BFS
        int md = Math.abs(data.getEntranceX()-data.getExitX()) + Math.abs(data.getEntranceY()-data.getExitY());
        return md + 20;
    }

    // Generate a genome that tends to move toward the goal while avoiding walls
    private int[] generateDirectedGenome(int length) {
        int[] g = new int[length];
        int x = data.getEntranceX(), y = data.getEntranceY();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < length; i++) {
            int mv;
            // Mostly choose directed moves; occasional random to escape dead-ends
            if (r.nextDouble() < 0.85) {
                mv = chooseDirectedMove(x, y, data.getExitX(), data.getExitY());
            } else {
                mv = r.nextInt(4);
            }
            g[i] = mv;
            int nx = x + directions[mv][0];
            int ny = y + directions[mv][1];
            if (!data.inArea(nx, ny) || data.getMazeChar(nx,ny)!=MazeData.ROAD) continue;
            x = nx; y = ny;
            if (x==data.getExitX() && y==data.getExitY()) break;
        }
        return g;
    }

    private static class Position {
        int x, y; Position prev;
        Position(int x, int y, Position prev){ this.x=x; this.y=y; this.prev=prev; }
    }

    private int findPath(Node p) {
        int steps = 0;
        Node cur = p;
        while (cur != null) {
            data.result[cur.x][cur.y] = true;
            cur = cur.prev;
            steps++;
        }
        return steps;
    }


    private void setData(int x, int y, boolean isPath) {
        if (cancelled) return;
        if (data.inArea(x, y)) {
            data.path[x][y] = isPath;
        }
        frame.render(data);
        try {
            MazeUtil.pause(frame.getDelayMs());
        } catch (Exception e) {
            // ignore
        }
    }


    // No Position class needed after switching to Dijkstra's Node representation

    private class Node {
        private int x, y;
        private int cost;
        private Node prev;

        private Node(int x, int y, int cost, Node prev) {
            this.x = x;
            this.y = y;
            this.cost = cost;
            this.prev = prev;
        }
    }

    public static void main(String[] args) {
        new Main().initFrame();
    }

}
