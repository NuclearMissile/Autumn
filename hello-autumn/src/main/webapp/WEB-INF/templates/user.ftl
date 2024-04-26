<#if username?has_content>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Welcome ${username}!</title>
    </head>
    <body>
    <h1>Index Page</h1>
    <p>Welcome, ${username}!</p>
    <p><a href="/user/logout">Logout</a></p>
    </body>
    </html>
<#else>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Login</title>
    </head>
    <body>
    <h1>Login Page</h1>
    <form method="post" action="/user/login">
        <p>Please Login</p>
        <p>User Name: <input type="text" name="username"></p>
        <p>Password: <input type="password" name="password"></p>
        <p><button type="submit">Login</button></p>
    </form>
    </body>
    </html>
</#if>