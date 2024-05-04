<#include "_head.html">
<body>
<#include "_nav.html">

<div class="container" style="padding-top: 90px">
    <div class="row">
        <div class="col-12">
            <#if error??>
                <div class="alert alert-warning alert-dismissible fade show">
                    ${error}
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            </#if>
            <form method="post" action="/login">
                <div class="mb-3">
                    <label for="email" class="form-label">Email:</label>
                    <input name="email" id="email" class="form-control" maxlength="100"/>
                </div>
                <div class="mb-3">
                    <label for="password" class="form-label">Password:</label>
                    <input name="password" id="password" type="password" class="form-control" maxlength="100"/>
                </div>
                <div class="mb-3">
                    <button type="submit" class="btn btn-primary">Log In</button>
                    <a href="/register" class="ms-4">Do not have an account yet?</a>
                </div>
            </form>
        </div>
    </div>
</div>

<#include "_footer.html">

</body>