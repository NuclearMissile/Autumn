<#include "_head.html">

<body>
<#include "_nav.html">

<div class="container" style="padding-top: 90px">
    <div class="row">
        <div class="col-12">
            <p>Welcome, ${user.name}!</p>
            <p>Email: ${user.email}</p>
            <p><a href="/logoff">Log Off</a></p>
            <p><a href="/changePassword">Change Password</a></p>
        </div>
    </div>
</div>

</body>

<#include "_footer.html">