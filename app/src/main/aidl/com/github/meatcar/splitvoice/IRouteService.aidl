package com.github.meatcar.splitvoice;
import android.os.Bundle;
interface IRouteService {
    void destroy() = 16777114;
    Bundle inspect() = 1;
    Bundle route(in Bundle request) = 2;
}
