package com.laioffer.giligili.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laioffer.giligili.entity.Game;
import com.laioffer.giligili.external.TwitchClient;
import com.laioffer.giligili.external.TwitchException;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

@WebServlet(name = "GameServlet", value = "/game")
public class GameServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String gameName = request.getParameter("game_name");
        TwitchClient client = new TwitchClient();

        response.setContentType("application/json;charset=UTF-8");
        try {
            if (gameName != null)
                response.getWriter().print(new ObjectMapper().writeValueAsString(client.searchGame(gameName)));
            else
                response.getWriter().print(new ObjectMapper().writeValueAsString(client.topGames(0)));
        } catch (TwitchException e) {
            throw new ServletException();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }
}
