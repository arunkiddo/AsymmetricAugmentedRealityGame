package comm;

import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.port;
import static spark.Spark.post;

import java.net.HttpURLConnection;

import com.google.gson.Gson;

import models.GamesInformation;

public class WebAPI {
	private static final Gson gson = new Gson();

	public static void main(String[] args) {
		new WebAPI();
		while (true) {
			// Infinitive loop to keep server alive
		}
	}

	public WebAPI() {
		init();
	}

	private void init() {
		System.out.println("Listening to stuff");
		port(8080);
		enableCORS("*", "*", "*");

		/*
		 * Get information about older and current games
		 */
		get("/games", (request, response) -> {
			System.out.println("Get information about older and current games");
			response.status(HttpURLConnection.HTTP_OK);
			return getGameInfo();
		});

		/*
		 * Start a game
		 */
		post("/games", (request, response) -> {
			System.out.println("Start a game");
			return null;
		});

		/*
		 * Get information on a specific game
		 */
		get("/games/:gameid", (request, response) -> {
			System.out.println("Get information on a specific game");
			return null;
		});

		/*
		 * Join a game
		 */
		post("/games/:gameid", (request, response) -> {
			System.out.println("Join a game");
			return null;
		});

		/*
		 * Leave a game
		 */
		delete("/games/:gameid/:playerid", (request, response) -> {
			System.out.println("Leave a game");
			return null;
		});

		/*
		 * Defuse a bomb
		 */
		post("/games/:gameid/defuses", (request, response) -> {
			System.out.println("Defuse a bomb");
			return null;
		});

		/*
		 * Get information on defusal attempts
		 */
		get("/games/:gameid/defuses", (request, response) -> {
			System.out.println("Get information on defusal attempts");
			return null;
		});
	}

	private static void enableCORS(final String origin, final String methods, final String headers) {

		options("/*", (request, response) -> {

			String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
			if (accessControlRequestHeaders != null) {
				response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
			}

			String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
			if (accessControlRequestMethod != null) {
				response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
			}

			return "OK";
		});

		before((request, response) -> {
			response.header("Access-Control-Allow-Origin", origin);
			response.header("Access-Control-Request-Method", methods);
			response.header("Access-Control-Allow-Headers", headers);
			response.type("application/json");
		});
	}

	/* TEMPORARY STUFF */

	/**
	 * 
	 */
	private void startGame() {
		// TODO: Move to another file
	}

	/**
	 * 
	 */
	private String getGameInfo() {
		// TODO: Move to another file
		// TODO: Get real data
		GamesInformation gamesInformation = new GamesInformation();
		// gamesInformation.setTest("currentgame");

		return gson.toJson(gamesInformation);
	}

	/**
	 * 
	 * @param game
	 */
	private void getGameInfo(int game) {
		// TODO: Move to another file
	}

	/**
	 * 
	 * @param game
	 * @param player
	 */
	private void joinGame(int game, int player) {
		// TODO: Move to another file
	}

	/**
	 * 
	 * @param game
	 * @param player
	 */
	private void leaveGame(int game, int player) {
		// TODO: Move to another file
	}

	/**
	 * 
	 * @param game
	 * @param player
	 */
	private void defuseBomb(int game, int player) {
		// TODO: Move to another file
	}

	/**
	 * 
	 * @param game
	 */
	private void getDefuseInfo(int game) {
		// TODO: Move to another file
	}
}
