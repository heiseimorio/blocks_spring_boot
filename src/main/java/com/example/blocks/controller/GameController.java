package com.example.blocks.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.example.blocks.entity.Account;
import com.example.blocks.entity.Block;
import com.example.blocks.entity.Cell;
import com.example.blocks.entity.Game;
import com.example.blocks.entity.GameInfo;
import com.example.blocks.entity.Hand;
import com.example.blocks.entity.Kouho;
import com.example.blocks.entity.Message;
import com.example.blocks.entity.Notification;
import com.example.blocks.entity.Player;
import com.example.blocks.entity.PlayerInfo;
import com.example.blocks.entity.Record;
import com.example.blocks.repository.AccountRepository;
import com.example.blocks.repository.BlockRepository;
import com.example.blocks.repository.GameRepository;
import com.example.blocks.repository.PlayerRepository;
import com.example.blocks.repository.RecordRepository;
import com.example.blocks.service.Color;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/game")
public class GameController {

  private final static int[][][] BLOCK_SHAPE = { { { 0, 0 }, { 0, 1 }, { 1, 0 }, { 1, 1 } }, // 0:四角
      { { 0, 0 } }, // 1:１小竹の
      { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 } }, // 2:逆T(短かい方)
      { { 0, 0 }, { 1, 0 } }, // 3:２連続
      { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 3, 0 } }, // 4:４連続
      { { 0, 0 }, { 1, 0 }, { 1, 1 } }, // 5:「の３個
      { { 0, 1 }, { 1, 1 }, { 2, 1 }, { 2, 0 } }, // 6:「の４個
      { { 0, 0 }, { 1, 0 }, { 2, 0 } }, // 7:３連続
      { { 0, 1 }, { 1, 0 }, { 1, 1 }, { 2, 0 } }, // 8:半分卍(４個)

      { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 }, { 3, 1 } }, // 9: 寝てる
      { { 0, 2 }, { 1, 0 }, { 1, 1 }, { 1, 2 }, { 2, 2 } }, // 10: 逆T(長い方)
      { { 0, 0 }, { 0, 1 }, { 0, 2 }, { 1, 2 }, { 2, 2 } }, // 11: L(５個)
      { { 0, 1 }, { 1, 0 }, { 1, 1 }, { 2, 0 }, { 3, 0 } }, // 12: 半分卍(４個)
      { { 0, 1 }, { 0, 2 }, { 1, 1 }, { 2, 0 }, { 2, 1 } }, // 13: Z
      { { 0, 0 }, { 0, 1 }, { 0, 2 }, { 0, 3 }, { 0, 4 } }, // 14:５連続

      { { 0, 0 }, { 0, 1 }, { 0, 2 }, { 1, 1 }, { 1, 2 } }, // 15: 下半身太
      { { 1, 0 }, { 2, 0 }, { 0, 1 }, { 1, 1 }, { 0, 2 } }, // 16: Wみたいなの
      { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 0, 2 }, { 1, 2 } }, // 17: コの逆
      { { 1, 0 }, { 2, 0 }, { 0, 1 }, { 1, 1 }, { 1, 2 } }, // 18: 手裏剣風
      { { 1, 0 }, { 2, 1 }, { 0, 1 }, { 1, 1 }, { 1, 2 } }, // 19: 十字架
      { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 }, { 3, 1 } }, // 20: トンファー

  };

  private static final int[][] NEXT_POSITIONS = {

      { 0, 2 }, // 四角
      { 3, 0 }, // １小竹の
      { 4, 2 }, // 逆T(短かい方)
      { 7, 0 }, // ２連続
      { 9, 3 }, // ４連続
      { 12, 0 }, // 「の３個
      { 15, 2 }, // 「の４個
      { 17, 0 }, // ３連続
      { 20, 2 }, // 半分卍(４個)

      { 0, 6 }, // 寝てるやつ
      { 5, 5 }, // 逆T(長い方)
      { 9, 5 }, // L(５個)
      { 13, 6 }, // 半分卍(４個)
      { 18, 5 }, // Z
      { 22, 5 }, // ５連続

      { 0, 9 }, // 下半身太
      { 3, 9 }, // Wみたいなの
      { 7, 9 }, // コの逆
      { 11, 9 }, // 手裏剣風
      { 15, 9 }, // 十字架
      { 19, 10 }, // トンファー
  };

  // --- Autowired --------------------------------

  @Autowired
  GameRepository gameRepository;

  @Autowired
  PlayerRepository playerRepository;

  @Autowired
  BlockRepository blockRepository;

  @Autowired
  RecordRepository recordRepository;

  @Autowired
  AccountRepository accountRepository;

  @Autowired
  private SimpMessagingTemplate simpMessagingTemplate;

  // --- Mapping --------------------------------

  @GetMapping("/index")
  public String index(Model model, Principal principal) {

    // 念のためのログインチェック。WebSecurityConfigで設定しているのでnullでくることはないはず
    if (principal == null) {
      return "redirect:/login";
    }

    // アカウントリスト
    Iterable<Account> accounts = accountRepository.findAll();

    // 表示用のデータを作成していく
    List<GameInfo> games = new ArrayList<GameInfo>();

    // 過去に参加したゲームの一覧を取得
    List<Player> players =  playerRepository.findByAccountName(principal.getName());
    if (players != null) {
      for (Player player : players) {
        Optional<Game> game = gameRepository.findById(player.getGameId());
        if (game == null) {
          System.out.println("game id is not found!!");
        } else {
          List<Player> players2 = playerRepository.findByGameId(player.getGameId());
          GameInfo gameInfo = new GameInfo();
          gameInfo.setId(game.get().getId());
          gameInfo.setDate(game.get().getDate());
          gameInfo.setAuthor(game.get().getAuthor());
          gameInfo.setPlayers(players2);
          games.add(gameInfo);
        }
      }
    }

    model.addAttribute("username", principal.getName());
    model.addAttribute("accounts", accounts);
    model.addAttribute("games", games);
    return "game/index";
  }

  /**
   * ゲーム開始処理
   */
  @PostMapping(path = "/start")
  public ModelAndView start(
    Principal principal,
    @RequestParam(name = "player-red", defaultValue = "") String playerRed,
    @RequestParam(name = "player-blue", defaultValue = "") String playerBlue,
    @RequestParam(name = "player-green", defaultValue = "") String playerGreen,
    @RequestParam(name = "player-yellow", defaultValue = "") String playerYellow) {

    // 現在の日時を取得
    LocalDateTime date1 = LocalDateTime.now();
    DateTimeFormatter dtformat1 = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    String fdate1 = dtformat1.format(date1);

    // gameテーブルにデータを新規作成
    Game g = new Game();
    g.setNowPlayer(1); // 最初はPlayer0
    g.setDate(fdate1); // 今の日時
    g.setAuthor(principal.getName());
    g.setCounter(1);    // カウンター
    g = gameRepository.save(g);

    String[] selectPlayers = new String[4];
    selectPlayers[0] = playerRed;
    selectPlayers[1] = playerBlue;
    selectPlayers[2] = playerGreen;
    selectPlayers[3] = playerYellow;

    int cpuCounter = 1;

    // 人数分ループ
    for (int p = 0; p < 4; p++) {
      if (selectPlayers[p].isEmpty()) {
        Player player = new Player();
        player.setGameId(g.getId());
        player.setCpu("cpu" + cpuCounter++);
        player.setNumber(p + 1);
        player.setPass(false);
        player.setZanBlockCount(BLOCK_SHAPE.length);
        player.setPoint(0);
        playerRepository.save(player);
      } else {
        Player player = new Player();
        player.setGameId(g.getId());
        player.setAccountName(selectPlayers[p]);
        player.setNumber(p + 1);
        player.setPass(false);
        player.setZanBlockCount(BLOCK_SHAPE.length);
        player.setPoint(0);
        playerRepository.save(player);
      }


      // ブロックの数の分だけループ
      for (int i = 0; i < BLOCK_SHAPE.length; i++) {
        // ブロックのデータをDBに保存
        Block block = new Block();
        block.setBlockType(i); // ブロックの種類
        block.setPlayer(p + 1); // プレイヤー番号(１始まり)
        block.setGameId(g.getId()); // gameテーブルのid
        block.setStatus(Block.STATUS_NOT_SETTED); // 状態=未セット
        blockRepository.save(block);
      }
    }

    // play画面へリダイレクト
    ModelAndView modelAndView = new ModelAndView("redirect:/game/show");
    modelAndView.addObject("id", g.getId());
    return modelAndView;
  }


  /**
   * ゲーム盤を表示する
   */
  @RequestMapping("/show")
  public String play(@RequestParam(name = "id", required = true, defaultValue = "0") int id, Principal principal, Model model) {

    // idでgameテーブルを検索する
    Optional<Game> ret = gameRepository.findById(id);
    if (!ret.isPresent()) {
      System.out.println("ERROR! game id is not found! id=" + id);
      return "redirect:/";
    }
    int nowPlayer = ret.get().getNowPlayer();
    String nowPlayerColor = Color.getColor(nowPlayer);


    // 表示用のセル配置を作成
    String[][] cells = new String[20][20];
    for (int y = 0; y < cells.length; y++) {
      for (int x = 0; x < cells[y].length; x++) {
        cells[y][x] = Color.DEFAULT;
      }
    }

    // セット済みのブロック情報を取得
    List<Block> settedBlocks = blockRepository.findByGameIdAndStatus(id, Block.STATUS_SETTED);
    for (Block block : settedBlocks) {
      drawBlock(block.getBlockType(), block.getX(), block.getY(), cells, Color.getColor(block.getPlayer()),
          block.getAngle() == null ? 0 : block.getAngle(), block.isFlip());
    }

    // まだ置いていないブロックの配列を作成
    Cell[][] nexts = new Cell[12][23];
    for (int y = 0; y < nexts.length; y++) {
      for (int x = 0; x < nexts[y].length; x++) {
        nexts[y][x] = new Cell(Color.DEFAULT, 0);
      }
    }



    // プレイヤー情報を取得
    // List<Player> players = playerRepository.findByGameIdAndNumber(ret.get().getId(), nowPlayer);
    List<Player> players = playerRepository.findByGameId(ret.get().getId());
    if (players == null || players.size() <= 0) {
      System.out.println("ERROR! player number is not found! id=" + ret.get().getId() );
      return "redirect:/";
    }

    boolean isLoginUserPass = false;
    boolean isAllPass = true;
    PlayerInfo minPlayer = null;
    int minZansu = 99;
    Player p = null;
    List<PlayerInfo> playersInfo = new ArrayList<PlayerInfo>();
    for (Player player : players) {

      PlayerInfo playerInfo = new PlayerInfo();

      if (player.getAccountName() != null) {
        playerInfo.setName(player.getAccountName());
      } else {
        playerInfo.setName(player.getCpu());
      }

      boolean isOkeru = false;

      // ログインユーザだったら
      if (player.getAccountName() != null &&  player.getAccountName().equals(principal.getName())) {
        // 未セットのブロックを描画する
        List<Block> notSetBlocks = blockRepository.findByGameIdAndStatusAndPlayer(id, Block.STATUS_NOT_SETTED, player.getNumber());
        for (Block block : notSetBlocks) {
          drawNextBlock(block, nexts, Color.getColor(block.getPlayer()));
        }

      }

      // 現在のプレイヤー
      if (player.getNumber() == nowPlayer) {
        List<Block> notSetBlocks2 = blockRepository.findByGameIdAndStatusAndPlayer(id, Block.STATUS_NOT_SETTED, nowPlayer);

        p = player;
        playerInfo.setBlockZansu(notSetBlocks2.size());
        isOkeru = checkOkeru(notSetBlocks2, cells, nowPlayerColor);
        isLoginUserPass = isOkeru == false;

      // 現在のプレイヤー意外
      } else {

        List<Block> tempBlocks = blockRepository.findByGameIdAndStatusAndPlayer(id, Block.STATUS_NOT_SETTED,
            player.getNumber());
        playerInfo.setBlockZansu(tempBlocks.size());
        isOkeru = checkOkeru(tempBlocks, cells, Color.getColor(player.getNumber()));
      }
      playerInfo.setColor(Color.getColorKanji(player.getNumber()));
      playerInfo.setPass(isOkeru == false);
      playerInfo.setPoint(player.getPoint());
      playersInfo.add(playerInfo);

      // 全員パスかどうかのチェック
      if (playerInfo.isPass() == false) {
        isAllPass = false;
      }

      // 残数の最小値
      if (minZansu > playerInfo.getBlockZansu()) {
        minZansu = playerInfo.getBlockZansu();
        minPlayer = playerInfo;
      }
    }

    // ログインユーザの手番かどうか
    boolean isLoginUserNow = p.getAccountName() != null && p.getAccountName().equals(principal.getName());


    // プレイヤーの名前
    String nowPlayerName = p.getAccountName();
    if (nowPlayerName == null) {
      nowPlayerName = p.getCpu();
    }

    model.addAttribute("cells", cells);
    model.addAttribute("nexts", nexts);
    model.addAttribute("nowPlayer", nowPlayer);
    model.addAttribute("nowPlayerColor", nowPlayerColor);
    model.addAttribute("id", ret.get().getId());
    model.addAttribute("isLoginUserNow", isLoginUserNow);
    model.addAttribute("isLoginUserPass", isLoginUserPass);
    model.addAttribute("nowPlayerName", nowPlayerName);
    model.addAttribute("playersInfo", playersInfo);
    model.addAttribute("isAllPass", isAllPass);
    model.addAttribute("minPlayer", minPlayer);

    return "game/show";
  }

  /**
   * 配置の候補を表示する
   */
  @RequestMapping("/kouho")
  public String kouho(Model model, @RequestParam(name = "id", required = true, defaultValue = "0") int id,
      @RequestParam(name = "block", required = true, defaultValue = "0") int selectBlock,
      @RequestParam(name = "angle", required = false, defaultValue = "0") int angle,
      @RequestParam(name = "flip", required = false, defaultValue = "false") boolean flip) {

    // idでgameテーブルを検索する
    Optional<Game> ret = gameRepository.findById(id);
    if (!ret.isPresent()) {
      System.out.println("ERROR! game id is not found! id=" + id);
      return "redirect:/";
    }
    int nowPlayer = ret.get().getNowPlayer();
    String nowPlayerColor = Color.getColor(nowPlayer);

    // まだ置いていないブロックの配列を作成
    Cell[][] nexts = new Cell[12][23];
    for (int y = 0; y < nexts.length; y++) {
      for (int x = 0; x < nexts[y].length; x++) {
        nexts[y][x] = new Cell(Color.DEFAULT, 0);
      }
    }
    List<Block> notSetBlocks = blockRepository.findByGameIdAndStatusAndPlayer(id, Block.STATUS_NOT_SETTED, nowPlayer);
    for (Block block : notSetBlocks) {
      String color = Color.getColor(block.getPlayer());
      if (block.getBlockType() == selectBlock) {
        color = Color.getKouhoColor(block.getPlayer());
      }
      drawNextBlock(block, nexts, color);
    }

    // 表示用のセル配置を作成
    String[][] cells = new String[20][20];
    for (int y = 0; y < cells.length; y++) {
      for (int x = 0; x < cells[y].length; x++) {
        cells[y][x] = Color.DEFAULT;
      }
    }

    // セット済みのブロック情報を取得
    List<Block> settedBlocks = blockRepository.findByGameIdAndStatus(id, Block.STATUS_SETTED);
    for (Block block : settedBlocks) {
      drawBlock(block.getBlockType(), block.getX(), block.getY(), cells, Color.getColor(block.getPlayer()),
          block.getAngle() == null ? 0 : block.getAngle(), block.isFlip());
    }

    // 置ける場所の候補リスト
    List<Kouho> kouhoList = new ArrayList<Kouho>(); // 置ける場所の候補リスト
    for (int y = 0; y < cells.length; y++) {
      for (int x = 0; x < cells[y].length; x++) {
        if (checkBlock(selectBlock, x, y, cells, nowPlayerColor, angle, flip)) {

          // 候補用の色を取得
          String color = Color.getKouhoColor(nowPlayer);

          // ここに置いた場合の絵を書く
          String[][] cells2 = new String[20][20];
          for (int i = 0; i < cells.length; i++) {
            cells2[i] = cells[i].clone();
          }

          drawBlock(selectBlock, x, y, cells2, color, angle, flip);

          // 候補をリストに追加
          Kouho kouho = new Kouho(x, y, cells2);
          kouhoList.add(kouho);
        }
      }
    }

    model.addAttribute("nexts", nexts);
    model.addAttribute("selectBlock", selectBlock);
    model.addAttribute("kouhoList", kouhoList);
    model.addAttribute("angle", angle);
    model.addAttribute("nowPlayer", nowPlayer);
    model.addAttribute("nowPlayerColor", nowPlayerColor);
    model.addAttribute("id", ret.get().getId());
    model.addAttribute("flip", flip);

    return "game/kouho";
  }

  /**
   * ブロックを置いたアクション
   */
  /*
  @RequestMapping("/oku")
  public ModelAndView oku(Model model,
      @RequestParam(name = "block", required = true, defaultValue = "0") int selectBlock,
      @RequestParam(name = "id", required = true, defaultValue = "0") int id,
      @RequestParam(name = "x", required = true, defaultValue = "-1") int x,
      @RequestParam(name = "y", required = true, defaultValue = "-1") int y,
      @RequestParam(name = "angle", required = true, defaultValue = "angle") int angle,
      @RequestParam(name = "flip", required = true, defaultValue = "false") boolean flip,
      @RequestParam(name = "pass", required = true, defaultValue = "false") boolean pass,
      @RequestParam(name = "player", required = true, defaultValue = "-1") int playerNumber) {

    // idでgameテーブルを検索する
    Optional<Game> ret = gameRepository.findById(id);
    if (!ret.isPresent()) {
      System.out.println("ERROR! game id is not found! id=" + id);
      return new ModelAndView("redirect:/");
    }
    Game game = ret.get();
    if(playerNumber != game.getNowPlayer()) {
        // play画面へリダイレクト
        ModelAndView modelAndView = new ModelAndView("redirect:/game/show");
        modelAndView.addObject("id", id);
        return modelAndView;
    }
    // プレイヤーの情報
    List<Player> players = playerRepository.findByGameIdAndNumber(id, game.getNowPlayer());
    if (players == null || players.size() <= 0) {
      System.out.println("ERROR! player is not found! id=" + id + ", number=" + game.getNowPlayer());
      return new ModelAndView("redirect:/");
    }
    Player player = players.get(0);

    // 現在操作中のプレイヤーの選択ブロックを取得
    if (pass == false) {
      List<Block> blocks = blockRepository.findByGameIdAndPlayerAndBlockType(id, game.getNowPlayer(), selectBlock);
      if (blocks == null || blocks.size() <= 0) {
        System.out.println("ERROR! selectBlock is not found! selectBlock=" + selectBlock);
        return new ModelAndView("redirect:/");
      }
      Block block = blocks.get(0);
      if (block.getStatus() == Block.STATUS_NOT_SETTED) { // まだセットされていない場合のみ
        block.setStatus(Block.STATUS_SETTED);
        block.setX(x);
        block.setY(y);
        block.setAngle(angle);
        block.setFlip(flip);
        blockRepository.save(block);

        // ポイント(置いたブロックのセル数)を加算する
        int point = BLOCK_SHAPE[selectBlock].length + player.getPoint();

        // 残数を減らす、ポイントを増やす
        player.setZanBlockCount(player.getZanBlockCount()  - 1);
        player.setPoint(point);
        player.setPass(player.getZanBlockCount() == 0);
        playerRepository.save(player);
      }
    } else {
      // 初めてのパスの時は保存する
      if (player.isPass() == false) {
        player.setPass(true);
        playerRepository.save(player);
      }
    }

    // 記録をつける
    Record record = new Record();
    record.setGameId(game.getId());
    record.setNumber(game.getCounter());
    record.setBlockType(selectBlock);
    record.setX(x);
    record.setY(y);
    record.setAngle(angle);
    record.setPass(pass);
    recordRepository.save(record);

    // 次のプレイヤーに移動
    game.goNextPlayer();
    gameRepository.save(game);
    model.addAttribute("flip", flip);
    // play画面へリダイレクト
    ModelAndView modelAndView = new ModelAndView("redirect:/game/show");
    modelAndView.addObject("id", id);
    return modelAndView;
  }
  */

  /**
   * ブロックを置いたアクション
   */
  @MessageMapping("/oku")
  public String okuByJs(Message message, Principal principal, @Header("simpSessionId") String sessionId) throws Exception {

    // idでgameテーブルを検索する
    Optional<Game> ret = gameRepository.findById(message.getId());
    if (!ret.isPresent()) {
      System.out.println("ERROR! game id is not found! id=" + message.getId());
      // return new ModelAndView("redirect:/");
    }
    Game game = ret.get();

    // プレイヤーの情報
    List<Player> players = playerRepository.findByGameIdAndNumber(message.getId(), game.getNowPlayer());
    if (players == null || players.size() <= 0) {
      System.out.println("ERROR! player is not found! message.getId()=" + message.getId() + ", number=" + game.getNowPlayer());
      // return new ModelAndView("redirect:/");
    }
    Player player = players.get(0);

    // 現在操作中のプレイヤーの選択ブロックを取得
    if (message.isPass() == false) {
      setBlock(message.getId(), player, message.getSelectBlock(), message.getX(), message.getY(), message.getAngle());

    } else {
      // 初めてのパスの時は保存する
      if (player.isPass() == false) {
        player.setPass(true);
        playerRepository.save(player);
      }
    }

    // 記録をつける
    Record record = new Record();
    record.setGameId(game.getId());
    record.setNumber(game.getCounter());
    record.setBlockType(message.getSelectBlock());
    record.setX(message.getX());
    record.setY(message.getY());
    record.setAngle(message.getAngle());
    record.setPass(message.isPass());
    recordRepository.save(record);

    // 次のプレイヤーに移動
    for (int i = 0; i < 4; i++) {
      game.goNextPlayer();
      gameRepository.save(game);

      // ゲームに参加している全メンバーへ通知する
      Notification notification =  new Notification("HelloHello");
      simpMessagingTemplate.convertAndSend("/game/" + game.getId() + "/notification", notification);

      // 次のプレイヤー
      List<Player> nextPlayers = playerRepository.findByGameIdAndNumber(message.getId(), game.getNowPlayer());
      if (nextPlayers == null || nextPlayers.size() <= 0) {
        System.out.println("ERROR! player is not found! message.getId()=" + message.getId() + ", number=" + game.getNowPlayer());
        // return new ModelAndView("redirect:/");
        continue;
      }
      Player nextPlayer = nextPlayers.get(0);

      // 次の人が手詰まり状態なら飛ばす
      if (nextPlayer.isPass()) {
        continue;
      }

      // 次の人がコンピュータの場合
      if (nextPlayer.getCpu() != null && nextPlayer.getCpu().isEmpty() == false) {
        // CPUの手番の場合は、CPUの手を作成
        List<Block> notSetBlocks3 = blockRepository.findByGameIdAndStatusAndPlayer(game.getId(), Block.STATUS_NOT_SETTED, nextPlayer.getNumber());

        // 表示用のセル配置を作成
        String[][] cells = new String[20][20];
        for (int y = 0; y < cells.length; y++) {
          for (int x = 0; x < cells[y].length; x++) {
            cells[y][x] = Color.DEFAULT;
          }
        }

        // セット済みのブロック情報を取得
        List<Block> settedBlocks = blockRepository.findByGameIdAndStatus(game.getId(), Block.STATUS_SETTED);
        for (Block block : settedBlocks) {
          drawBlock(block.getBlockType(), block.getX(), block.getY(), cells, Color.getColor(block.getPlayer()),
              block.getAngle() == null ? 0 : block.getAngle());
        }

        // CPUの手を考える
        Hand hand = makeCpuHand(cells, notSetBlocks3, nextPlayer.getCpu(), Color.getColor(nextPlayer.getNumber()));

        Thread.sleep(2000); // simulated delay

        // ブロックを置く
        setBlock(game.getId(), nextPlayer, hand.getBlockType(), hand.getX(), hand.getY(), hand.getAngle());


      } else {
        // 次の人が人の場合はループを抜ける
        break;
      }

    }



    return "success notification";
  }



  // --- Private Methods --------------------------------

  /***
   * ブロックを90度回転、反転させるメソッド
   *
   * @param oldShape 選択されたブロック
   * @param angle 角度
   * @param flip 反転するかどうか
   * @return 90度回転、もしくは反転したブロックの形
   */
  private int[][] calcBlockShape(int[][] oldShape, int angle, boolean flip) {
    if (angle == 0 && flip == false) {
      return oldShape;
    }
    String[][] cells = new String[5][5];
    String[][] cells2 = new String[5][5];

    // まず、左上を始点として角度なしで描く
    drawBlock(oldShape, 0, 0, cells, "ZZZ");

    for (int a = 0; a < angle; a++) {
      // 90度回転
      for (int x = 0; x < 5; x++) {
        for (int y = 0; y < 5; y++) {
          cells2[y][x] = cells[5 - 1 - x][y];
        }
      }



      // cells2 -> cells
      for (int x = 0; x < 5; x++) {
        cells[x] = cells2[x].clone();
      }

    }

    // 反転flgがあれば、反転させる
    if (flip) {
      for (int x = 0; x < 5; x++) {
        for (int y = 0; y < 5; y++) {
          cells2[y][x] = cells[y][4 - x];
        }
      }
       // cells2 -> cells
       for (int x = 0; x < 5; x++) {
        cells[x] = cells2[x].clone();
      }
    }

    int[][] shape = new int[oldShape.length][2];

    // ZZZ が入っている座標だけを抜き出す
    int i = 0;
    for (int y = 0; y < 5; y++) {
      for (int x = 0; x < 5; x++) {
        if (cells2[y][x] != null && cells2[y][x].equals("ZZZ")) {
          shape[i][0] = x;
          shape[i][1] = y;
          i++;
        }
      }
    }

    // x、yの最小値を調べる
    int minX = 99, minY = 99;
    for (i = 0; i < oldShape.length; i++) {
      if (shape[i][0] < minX) {
        minX = shape[i][0];
      }
      if (shape[i][1] < minY) {
        minY = shape[i][1];
      }
    }
    // 最小値が０じゃない場合はずれてるので、最小値が０になるようずらす
    for (i = 0; i < oldShape.length; i++) {
      if (minX > 0) {
        shape[i][0] -= minX;
      }
      if (minY > 0) {
        shape[i][1] -= minY;
      }
    }


    return shape;
  }

  /**
   * ブロックの色表示
   */
  private void drawBlock(int index, int x, int y, String[][] cells, String color, int angle, boolean flip) {
    int[][] block = BLOCK_SHAPE[index];
    block = calcBlockShape(block, angle, flip);

    drawBlock(block, x, y, cells, color);
  }

  private void drawBlock(int[][] block, int x, int y, String[][] cells, String color) {
    for (int[] position : block) {
      cells[y + position[1]][x + position[0]] = color;
    }
  }

  /**
   * ブロック候補の表示
   */
  private void drawNextBlock(Block block, Cell[][] cells, String color) {
    int index = block.getBlockType();
    int x = NEXT_POSITIONS[block.getBlockType()][0];
    int y = NEXT_POSITIONS[block.getBlockType()][1];
    Cell cell = new Cell(color, block.getBlockType());
    for (int[] position : BLOCK_SHAPE[index]) {
      cells[y + position[1]][x + position[0]] = cell;
    }
  }

  /**
   * ブロックを置けるかどうかのチェック
   */
  private boolean checkBlock(int index, int x, int y, String[][] cells, String color, int angle, boolean flip) {
    int[][] block = BLOCK_SHAPE[index];
    block = calcBlockShape(block, angle, flip);
    boolean isCheck = false;

    for (int[] position : block) {
      int newY = y + position[1];
      int newX = x + position[0];

      // はみ出てたらダメ！
      if (cells.length <= newY) {
        return false;
      }
      if (cells[newY].length <= newX) {
        return false;
      }

      // すでにあってもダメ！
      if (cells[newY][newX].equals(Color.DEFAULT) == false) {
        return false;
      }

      // 右隣が同じ色ならダメ
      if (newX < cells[newY].length - 1 && cells[newY][newX + 1].equals(color)) {
        return false;
      }
      // 左隣が同じ色ならダメ
      if (newX > 0 && cells[newY][newX - 1].equals(color)) {
        return false;
      }
      // 下隣が同じ色ならダメ
      if (newY < cells.length - 1 && cells[newY + 1][newX].equals(color)) {
        return false;
      }
      // 上隣が同じ色ならダメ
      if (newY > 0 && cells[newY - 1][newX].equals(color)) {
        return false;
      }

      // 右上が同じ色ならOK
      if (newY > 0 && newX < cells[newY].length - 1 && cells[newY - 1][newX + 1].equals(color)) {
        isCheck = true;
      }

      // 左上が同じ色ならOK
      if (newY > 0 && newX > 0 && cells[newY - 1][newX - 1].equals(color)) {
        isCheck = true;
      }

      // 右下が同じ色ならOK
      if (newY < cells.length - 1 && newX < cells[newY].length - 1 && cells[newY + 1][newX + 1].equals(color)) {
        isCheck = true;
      }

      // 左下が同じ色ならOK
      if (newY < cells.length - 1 && newX > 0 && cells[newY + 1][newX - 1].equals(color)) {
        isCheck = true;
      }

      // 四角を踏んでてらOK
      if ((newX == 0 && newY == 0) || (newX == 0 && newY == cells[newY].length - 1)
          || (newX == cells.length - 1 && newY == 0) || (newX == cells.length - 1 && newY == cells[newY].length - 1)) {
        isCheck = true;
      }
    }
    return isCheck;
  }

  /**
   * CPUの手を作る
   */
  private Hand makeCpuHand(String[][] cells, List<Block> notSetBlocks, String cpuName, String color) {
    Hand hand = new Hand();

    // まずブロックのセル数が多い順に並べる
    Collections.sort(
      notSetBlocks,
      new Comparator<Block>() {
        @Override
        public int compare(Block b1, Block b2) {
          return BLOCK_SHAPE[b2.getBlockType()].length - BLOCK_SHAPE[b1.getBlockType()].length;
        }
      }
    );

    for (Block block : notSetBlocks) {

      // 全部のセルをチェックしていく
      for (int y = 0; y < cells.length; y++) {
        for (int x = 0; x < cells[y].length; x++) {

          // 回転させてチェックする
          for (int angle = 0; angle < 4; angle++) {
            for (int i = 0; i < 2; i++) {
              // チェック
              if (checkBlock(block.getBlockType(), x, y, cells, color, angle, (i == 1))) {
                hand.setX(x);
                hand.setY(y);
                hand.setBlockType(block.getBlockType());
                hand.setAngle((angle));
                return hand;
              }
            }
          }
        }
      }
    }

    // 打つ手なし
    hand.setPass(true);

    return hand;
  }

  /**
   * 置けるブロックがあるかどうか
   */
  private boolean checkOkeru(List<Block> notSetBlocks, String[][] cells, String color) {
    for (int b = 0; b < notSetBlocks.size(); b++) {
      for (int y = 0; y < cells.length; y++) {
        for (int x = 0; x < cells[y].length; x++) {
          for (int angle = 0; angle < 4; angle++) {
            for (int i = 0; i < 2; i++) {
              if (checkBlock(notSetBlocks.get(b).getBlockType(), x, y, cells, color, angle, (i == 1))) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  private void setBlock(int gameId, Player player, int selectBlock, int x, int y, int angle) {
    List<Block> blocks = blockRepository.findByGameIdAndPlayerAndBlockType(gameId, player.getNumber(), selectBlock);
      if (blocks == null || blocks.size() <= 0) {
        System.out.println("ERROR! selectBlock is not found! selectBlock=" + selectBlock);
        // return new ModelAndView("redirect:/");
      }
      Block block = blocks.get(0);
      if (block.getStatus() == Block.STATUS_NOT_SETTED) { // まだセットされていない場合のみ
        block.setStatus(Block.STATUS_SETTED);
        block.setX(x);
        block.setY(y);
        block.setAngle(angle);
        blockRepository.save(block);

        // ポイント(置いたブロックのセル数)を加算する
        int point = BLOCK_SHAPE[selectBlock].length + player.getPoint();

        // 残数を減らす、ポイントを増やす
        player.setZanBlockCount(player.getZanBlockCount()  - 1);
        player.setPoint(point);
        player.setPass(player.getZanBlockCount() == 0);
        playerRepository.save(player);
      }
  }

}
