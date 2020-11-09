/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import java.util.Formatter;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Stack;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

import static loa.Piece.*;
import static loa.Square.*;

/** Represents the state of a game of Lines of Action.
 *  @author Mridang Sheth
 */
class Board {

    /** Default number of moves for each side that results in a draw. */
    static final int DEFAULT_MOVE_LIMIT = 60;

    /** Pattern describing a valid square designator (cr). */
    static final Pattern ROW_COL = Pattern.compile("^[a-h][1-8]$");

    /** A Board whose initial contents are taken from INITIALCONTENTS
     *  and in which the player playing TURN is to move. The resulting
     *  Board has
     *        get(col, row) == INITIALCONTENTS[row][col]
     *  Assumes that PLAYER is not null and INITIALCONTENTS is 8x8.
     *
     *  CAUTION: The natural written notation for arrays initializers puts
     *  the BOTTOM row of INITIALCONTENTS at the top.
     */
    Board(Piece[][] initialContents, Piece turn) {
        initialize(initialContents, turn);
    }

    /** A new board in the standard initial position. */
    Board() {
        this(INITIAL_PIECES, BP);
    }

    /** A Board whose initial contents and state are copied from
     *  BOARD. */
    Board(Board board) {
        this();
        copyFrom(board);
    }

    /** Set my state to CONTENTS with SIDE to move. */
    void initialize(Piece[][] contents, Piece side) {
        _turn = side;
        _moveLimit = DEFAULT_MOVE_LIMIT;

        for (int i = 0, k = 0; i < contents.length; i++) {
            for (int j = 0; j < contents[i].length; j++, k++) {
                _board[k] = contents[i][j];
            }
        }
        _moves.clear();
        _whiteRegionSizes.clear();
        _blackRegionSizes.clear();
        _winnerKnown = false;
        _winner = null;
        _subsetsInitialized = false;
    }

    /** Set me to the initial configuration. */
    void clear() {
        initialize(INITIAL_PIECES, BP);
    }

    /** Set my state to a copy of BOARD. */
    void copyFrom(Board board) {
        if (board == this) {
            return;
        }
        for (int i = 0; i < board._board.length; i++) {
            this._board[i] = board._board[i];
        }
        _moves.clear();
        _whiteRegionSizes.clear();
        _blackRegionSizes.clear();

        for (Move m : board._moves) {
            this._moves.add(m);
        }

        for (Integer size : board._whiteRegionSizes) {
            this._whiteRegionSizes.add(size);
        }
        for (Integer size : board._blackRegionSizes) {
            this._blackRegionSizes.add(size);
        }

        this._turn = board._turn;
        this._moveLimit = board._moveLimit;
        this._winnerKnown = board._winnerKnown;
        this._winner = board._winner;
        this._subsetsInitialized = board._subsetsInitialized;

    }

    /** Return the contents of the square at SQ. */
    Piece get(Square sq) {
        return _board[sq.index()];
    }

    /** Set the square at SQ to V and set the side that is to move next
     *  to NEXT, if NEXT is not null. */
    void set(Square sq, Piece v, Piece next) {
        _board[sq.index()] = v;

        if (next != null) {
            _turn = next;
        }
    }

    /** Set the square at SQ to V, without modifying the side that
     *  moves next. */
    void set(Square sq, Piece v) {
        set(sq, v, null);
    }

    /** Set limit on number of moves by each side that results in a tie to
     *  LIMIT, where 2 * LIMIT > movesMade(). */
    void setMoveLimit(int limit) {
        if (2 * limit <= movesMade()) {
            throw new IllegalArgumentException("move limit too small");
        }
        _moveLimit = 2 * limit;
    }

    /** Assuming isLegal(MOVE), make MOVE. Assumes MOVE.isCapture()
     *  is false. */
    void makeMove(Move move) {
        assert isLegal(move);
        if (_board[move.getTo().index()] == _turn.opposite()) {
            move = move.captureMove();
        }
        _moves.add(move);
        set(move.getTo(), _board[move.getFrom().index()]);
        set(move.getFrom(), EMP);
        _subsetsInitialized = false;

        if (piecesContiguous(_turn)) {
            _winnerKnown = true;
            _winner = _turn;
        } else if (piecesContiguous(_turn.opposite())) {
            _winnerKnown = true;
            _winner = _turn.opposite();
        }

        _turn = _turn.opposite();

    }


    /** Retract (unmake) one move, returning to the state immediately before
     *  that move.  Requires that movesMade () > 0. */
    void retract() {
        assert movesMade() > 0;
        Move m = _moves.remove(_moves.size() - 1);
        set(m.getFrom(), _board[m.getTo().index()]);
        if (m.isCapture()) {
            set(m.getTo(), _board[m.getFrom().index()].opposite());
        }
        computeRegions();
        _turn = _turn.opposite();

        if (_winnerKnown) {
            _winnerKnown = false;
            _winner = null;
        }

    }

    /** Return the Piece representing who is next to move. */
    Piece turn() {
        return _turn;
    }

    /** Return true iff FROM - TO is a legal move for the player currently on
     *  move. */
    boolean isLegal(Square from, Square to) {
        if (!(Square.exists(from.col(), from.row())
                && Square.exists(to.col(), to.row()))) {
            return false;
        }
        if (_board[from.index()] != _turn) {
            return false;
        }
        if (_board[to.index()] == _turn) {
            return false;
        }
        if (!from.isValidMove(to)) {
            return false;
        }
        if (oppositeBlocked(from, to)) {
            return false;
        }
        if (numOfPiecesAlongLine(from, to) != from.distance(to)) {
            return false;
        }

        return true;
    }

    /** Return true iff MOVE is legal for the player currently on move.
     *  The isCapture() property is NOT ignored. */
    boolean isLegal(Move move) {
        if (move.isCapture()) {
            if (_board[move.getTo().index()] != _turn.opposite()) {
                return false;
            }
        }
        return isLegal(move.getFrom(), move.getTo());
    }

    /** Return a sequence of all legal moves from this position. */
    List<Move> legalMoves() {
        Move[][][] allPossibleMoves = Move.getAllMoves();
        List<Move> legal = new ArrayList<Move>();
        for (int i = 0; i < allPossibleMoves.length; i++) {
            for (int j = 0; j < allPossibleMoves[i].length; j++) {
                Move m = allPossibleMoves[i][j][0];
                if (m != null) {
                    if (isLegal(m)) {
                        legal.add(m);
                    }
                }
            }
        }
        return legal;
    }

    /** Return true iff the game is over (either player has all his
     *  pieces continguous or there is a tie). */
    boolean gameOver() {
        return winner() != null;
    }

    /** Return true iff SIDE's pieces are continguous. */
    boolean piecesContiguous(Piece side) {
        return getRegionSizes(side).size() == 1;
    }


    /** Return the winning side, if any.  If the game is not over, result is
     *  null.  If the game has ended in a tie, returns EMP. */
    Piece winner() {
        if (!_winnerKnown) {
            if (movesMade() >= _moveLimit) {
                _winner = EMP;
                _winnerKnown = true;
            }
            if (piecesContiguous(WP)) {
                _winnerKnown = true;
                _winner = WP;
            }
            if (piecesContiguous(BP)) {
                _winnerKnown = true;
                _winner = BP;
            }
        }
        return _winner;
    }

    /** Return the total number of moves that have been made (and not
     *  retracted).  Each valid call to makeMove with a normal move increases
     *  this number by 1. */
    int movesMade() {
        return _moves.size();
    }

    @Override
    public boolean equals(Object obj) {
        Board b = (Board) obj;
        return Arrays.deepEquals(_board, b._board) && _turn == b._turn;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(_board) * 2 + _turn.hashCode();
    }

    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("===%n");
        for (int r = BOARD_SIZE - 1; r >= 0; r -= 1) {
            out.format("    ");
            for (int c = 0; c < BOARD_SIZE; c += 1) {
                out.format("%s ", get(sq(c, r)).abbrev());
            }
            out.format("%n");
        }
        out.format("Next move: %s%n===", turn().fullName());
        return out.toString();
    }

    /** Return true if a move from FROM to TO is blocked by an opposing
     *  piece on the target square. */
    private boolean oppositeBlocked(Square from, Square to) {
        int distance = from.distance(to) - 1;
        int dir = from.direction(to);
        while (distance > 0) {
            from = from.moveDest(dir, 1);
            distance--;
            if (from != null && _board[from.index()] == _turn.opposite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of pieces along the line in
     * the direction of from to to.
     * @param from : The square from
     * @param to : The square to
     * @return The num of pieces.
     */
    private int numOfPiecesAlongLine(Square from, Square to) {
        int direction = from.direction(to);
        int oppdir = to.direction(from);
        int num = 1;
        Square traverseBack = from;
        Square traverseForward = from;
        while (traverseBack != null) {
            traverseBack = traverseBack.moveDest(oppdir, 1);
            if (traverseBack != null) {
                if (_board[traverseBack.index()] != EMP) {
                    num++;
                }
            }
        }
        while (traverseForward != null) {
            traverseForward = traverseForward.moveDest(direction, 1);
            if (traverseForward != null) {
                if (_board[traverseForward.index()] != EMP) {
                    num++;
                }
            }
        }

        return num;
    }

    /** Return the size of the as-yet unvisited cluster of squares
     *  containing P at and adjacent to SQ.  VISITED indicates squares that
     *  have already been processed or are in different clusters.  Update
     *  VISITED to reflect squares counted. */
    private int numContig(Square sq, boolean[] visited, Piece p) {

        if (_board[sq.index()] != p || visited[sq.index()]) {
            return 0;
        }
        int size = 0;
        Stack<Square> stack = new Stack<Square>();
        stack.push(sq);
        while (!stack.isEmpty()) {
            sq = stack.pop();

            if (_board[sq.index()] == p && !visited[sq.index()]) {
                size++;
                for (Square adj : sq.adjacent()) {
                    stack.push(adj);
                }
            }
            visited[sq.index()] = true;
        }

        return size;
    }


    /** Set the values of _whiteRegionSizes and _blackRegionSizes. */
    private void computeRegions() {
        if (_subsetsInitialized) {
            return;
        }
        _whiteRegionSizes.clear();
        _blackRegionSizes.clear();

        boolean[] visited = new boolean[_board.length];
        for (Square sq : ALL_SQUARES) {
            int size = numContig(sq, visited, WP);
            if (size != 0) {
                _whiteRegionSizes.add(size);
            }
        }

        visited = new boolean[_board.length];
        for (Square sq : ALL_SQUARES) {
            int size = numContig(sq, visited, BP);
            if (size != 0) {
                _blackRegionSizes.add(size);
            }
        }

        Collections.sort(_whiteRegionSizes, Collections.reverseOrder());
        Collections.sort(_blackRegionSizes, Collections.reverseOrder());
        _subsetsInitialized = true;
    }

    /** Return the sizes of all the regions in the current union-find
     *  structure for side S. */
    List<Integer> getRegionSizes(Piece s) {
        computeRegions();
        if (s == WP) {
            return _whiteRegionSizes;
        } else {
            return _blackRegionSizes;
        }
    }

    /** The standard initial configuration for Lines of Action (bottom row
     *  first). */
    static final Piece[][] INITIAL_PIECES = {
        { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
        { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP }
    };

    /** Current contents of the board.  Square S is at _board[S.index()]. */
    private final Piece[] _board = new Piece[BOARD_SIZE  * BOARD_SIZE];

    /** List of all unretracted moves on this board, in order. */
    private final ArrayList<Move> _moves = new ArrayList<>();
    /** Current side on move. */
    private Piece _turn;
    /** Limit on number of moves before tie is declared.  */
    private int _moveLimit;
    /** True iff the value of _winner is known to be valid. */
    private boolean _winnerKnown;
    /** Cached value of the winner (BP, WP, EMP (for tie), or null (game still
     *  in progress).  Use only if _winnerKnown. */
    private Piece _winner;

    /** True iff subsets computation is up-to-date. */
    private boolean _subsetsInitialized;

    /** List of the sizes of continguous clusters of pieces, by color. */
    private final ArrayList<Integer>
        _whiteRegionSizes = new ArrayList<>(),
        _blackRegionSizes = new ArrayList<>();
}
