/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import java.util.List;

import static loa.Piece.*;

/** An automated Player.
 *  @author Mridang Sheth
 */
class MachinePlayer extends Player {

    /** A position-score magnitude indicating a win (for white if positive,
     *  black if negative). */
    private static final int WINNING_VALUE = Integer.MAX_VALUE - 20;
    /** A magnitude greater than a normal value. */
    private static final int INFTY = Integer.MAX_VALUE;

    /** A new MachinePlayer with no piece or controller (intended to produce
     *  a template). */
    MachinePlayer() {
        this(null, null);
    }

    /** A MachinePlayer that plays the SIDE pieces in GAME. */
    MachinePlayer(Piece side, Game game) {
        super(side, game);
    }

    @Override
    String getMove() {
        Move choice;
        assert side() == getGame().getBoard().turn();
        choice = searchForMove();
        getGame().reportMove(choice);
        return choice.toString();
    }

    @Override
    Player create(Piece piece, Game game) {
        return new MachinePlayer(piece, game);
    }

    @Override
    boolean isManual() {
        return false;
    }

    /** Return a move after searching the game tree to DEPTH>0 moves
     *  from the current position. Assumes the game is not over. */
    private Move searchForMove() {
        Board work = new Board(getBoard());
        assert side() == work.turn();
        _foundMove = null;
        findMove(work, chooseDepth(), side(), -INFTY, INFTY);
        return _foundMove;
    }

    /** Find a move from position BOARD and return its value, recording
     *  the move found in _foundMove iff SAVEMOVE. The move
     *  should have maximal value or have value > BETA if SENSE==1,
     *  and minimal value or value < ALPHA if SENSE==-1. Searches up to
     *  DEPTH levels.  Searching at level 0 simply returns a static estimate
     *  of the board value and does not set _foundMove. If the game is over
     *  on BOARD, does not set _foundMove.
     *  @param alpha : The alpha value for pruning
     * @param beta : The beta value for pruning
     * @param board : The board
     * @param depth : The depth level
     * @param p : The maximiser piece
     *  */
    private int findMove(Board board, int depth,
                         Piece p, int alpha, int beta) {

        if (depth <= 0 || board.winner() != null
                || board.legalMoves() == null || board.legalMoves().isEmpty()) {
            return calcHeuristic(board, p, depth);
        }

        if (board.turn() == p) {
            int bestVal = -INFTY;
            Move bestMove = null;
            for (Move m : board.legalMoves()) {
                Board b = new Board(board);
                b.makeMove(m);
                int val = findMove(b, depth - 1, p, alpha, beta);
                if (bestVal < val) {
                    bestMove = m;
                }
                bestVal = Math.max(bestVal, val);
                alpha = Math.max(alpha, bestVal);
                if (beta <= alpha) {
                    break;
                }
            }
            _foundMove = bestMove;
            return bestVal;
        } else {
            int bestVal = INFTY;
            for (Move m : board.legalMoves()) {
                Board b = new Board(board);
                b.makeMove(m);
                int val = findMove(b, depth - 1, p, alpha, beta);
                bestVal = Math.min(bestVal, val);
                beta = Math.min(bestVal, beta);

                if (beta <= alpha) {
                    break;
                }
            }
            return bestVal;
        }

    }


    /** Return a search depth for the current position. */
    private int chooseDepth() {
        return 4;
    }

    /**
     * The heuristic of the board position.
     * @param b : The board to be analysed
     * @param p : The maximiser piece
     * @param depth : The depth of the game tree
     * @return : Calculated heuristic
     */
    private int calcHeuristic(Board b, Piece p, int depth) {
        int value;
        List<Integer> whiteSizes = b.getRegionSizes(WP);
        List<Integer> blackSizes = b.getRegionSizes(BP);
        if (b.winner() == p) {
            value = WINNING_VALUE + depth;
        } else if (b.winner() == p.opposite()) {
            value = -WINNING_VALUE - depth;
        } else {
            value = 1000 / whiteSizes.size() - 1000 / blackSizes.size()
                    + 10 * whiteSizes.get(0) - 10 * blackSizes.get(0);
            if (p == BP) {
                value *= -1;
            }
        }

        return value;
    }

    /** Used to convey moves discovered by findMove. */
    private Move _foundMove;


}
