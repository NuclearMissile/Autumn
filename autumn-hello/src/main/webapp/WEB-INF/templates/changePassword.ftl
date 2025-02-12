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
            <form method="post" action="/changePassword">
                <div class="mb-3">
                    <label for="old_password" class="form-label">Old password:</label>
                    <input name="old_password" id="old_password" type="password" class="form-control" maxlength="100"/>
                </div>
                <div class="mb-3">
                    <label for="new_password" class="form-label">New password:</label>
                    <input name="new_password" id="new_password" type="password" class="form-control" maxlength="100"/>
                </div>
                <div class="mb-3">
                    <label for="new_password_repeat" class="form-label">New password repeat:</label>
                    <input name="new_password_repeat" id="new_password_repeat" type="password" class="form-control"
                           maxlength="100"/>
                </div>
                <div class="mb-3">
                    <button type="submit" class="btn btn-primary">Change Password</button>
                    <a href="/" class="ms-4">Homepage</a>
                </div>
            </form>
        </div>
    </div>
</div>

<#include "_footer.html">

</body>